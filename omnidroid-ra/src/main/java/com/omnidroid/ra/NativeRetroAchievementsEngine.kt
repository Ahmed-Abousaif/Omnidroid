package com.omnidroid.ra

import com.omnidroid.lib.ra.RaAchievement
import com.omnidroid.lib.ra.RaAchievementBucket
import com.omnidroid.lib.ra.RaFileOpenDelegate
import com.omnidroid.lib.ra.RaGameInfo
import com.omnidroid.lib.ra.RaLeaderboard
import com.omnidroid.lib.ra.RaLeaderboardBucket
import com.omnidroid.lib.ra.RaLeaderboardScoreboard
import com.omnidroid.lib.ra.RaLeaderboardTracker
import com.omnidroid.lib.ra.RaModuleBuildInfo
import com.omnidroid.lib.ra.RaModuleLoadStatus
import com.omnidroid.lib.ra.RaPauseDecision
import com.omnidroid.lib.ra.RaServerError
import com.omnidroid.lib.ra.RaSubset
import com.omnidroid.lib.ra.RaUserGameSummary
import com.omnidroid.lib.ra.RaUserInfo
import com.omnidroid.lib.ra.RetroAchievementsEngine
import com.omnidroid.lib.ra.RetroAchievementsEvent
import com.omnidroid.lib.ra.RetroAchievementsSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class NativeRetroAchievementsEngine(
    okHttpClient: OkHttpClient,
    private val supportedAbiMajor: Int = SUPPORTED_ABI_MAJOR,
) : RetroAchievementsEngine,
    NativeBridgeCallbacks {
    private val bridge = NativeRetroAchievementsBridge(okHttpClient, this)
    private val nextToken = AtomicLong(1L)
    private val pendingAsync = ConcurrentHashMap<Long, CompletableDeferred<Pair<Int, String>>>()
    private val sessionAttached = AtomicBoolean(false)

    private val _events =
        MutableSharedFlow<RetroAchievementsEvent>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.SUSPEND,
        )
    private val _moduleStatus = MutableStateFlow<RaModuleLoadStatus>(RaModuleLoadStatus.Missing)
    private val _activeGame = MutableStateFlow<RaGameInfo?>(null)
    private val _activeSummary = MutableStateFlow<RaUserGameSummary?>(null)
    private val _richPresence = MutableStateFlow("")
    private val _isHardcoreEffective = MutableStateFlow(false)
    private val _isDisconnected = MutableStateFlow(false)

    override val events: SharedFlow<RetroAchievementsEvent> = _events.asSharedFlow()
    override val moduleStatus: StateFlow<RaModuleLoadStatus> = _moduleStatus.asStateFlow()
    override val activeGame: StateFlow<RaGameInfo?> = _activeGame.asStateFlow()
    override val activeSummary: StateFlow<RaUserGameSummary?> = _activeSummary.asStateFlow()
    override val richPresence: StateFlow<String> = _richPresence.asStateFlow()
    override val isHardcoreEffective: StateFlow<Boolean> = _isHardcoreEffective.asStateFlow()
    override val isDisconnected: StateFlow<Boolean> = _isDisconnected.asStateFlow()

    override suspend fun loadModule(verifiedPath: String): Result<RaModuleBuildInfo> =
        withContext(Dispatchers.IO) {
            if (sessionAttached.get()) {
                return@withContext Result.failure(
                    IllegalStateException("Cannot hot-swap RA module while a game session is active"),
                )
            }
            _moduleStatus.value = RaModuleLoadStatus.Loading
            if (!bridge.ensureBridgeLoaded()) {
                val msg = "Native RA bridge library unavailable"
                _moduleStatus.value = RaModuleLoadStatus.Failed(msg)
                return@withContext Result.failure(UnsatisfiedLinkError(msg))
            }

            val rawInfo = arrayOfNulls<String>(5)
            val err = bridge.nativeLoadModule(verifiedPath, supportedAbiMajor, rawInfo)
            if (err != null) {
                _moduleStatus.value = RaModuleLoadStatus.Failed(err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            val buildInfo =
                RaModuleBuildInfo(
                    abiMajor = rawInfo[0]?.toIntOrNull() ?: supportedAbiMajor,
                    abiMinor = rawInfo[1]?.toIntOrNull() ?: 0,
                    moduleVersion = rawInfo[2].orEmpty(),
                    rcheevosVersion = rawInfo[3].orEmpty(),
                    gitHash = rawInfo[4].orEmpty(),
                )
            _moduleStatus.value = RaModuleLoadStatus.Loaded(buildInfo, verifiedPath)
            _isHardcoreEffective.value = bridge.nativeGetHardcore()
            Result.success(buildInfo)
        }

    override fun unloadModule() {
        sessionAttached.set(false)
        bridge.activeFileOpenDelegate = null
        failAllPendingAsync("RetroAchievements module unloaded")
        bridge.cancelAllHttp()
        if (isModuleLoaded()) {
            bridge.nativeUnloadModule()
        }
        _activeGame.value = null
        _activeSummary.value = null
        _richPresence.value = ""
        _isHardcoreEffective.value = false
        _isDisconnected.value = false
        _moduleStatus.value = RaModuleLoadStatus.Missing
    }

    override fun isModuleLoaded(): Boolean = _moduleStatus.value is RaModuleLoadStatus.Loaded

    override fun setUserAgent(userAgent: String) {
        if (isModuleLoaded()) bridge.nativeSetUserAgent(userAgent)
    }

    override fun applySettings(settings: RetroAchievementsSettings) {
        if (!isModuleLoaded()) {
            _isHardcoreEffective.value = false
            return
        }
        bridge.nativeApplySettings(
            hardcore = settings.hardcore,
            encore = settings.encoreMode,
            unofficial = settings.unofficialEnabled,
            spectator = settings.spectatorMode,
        )
        _isHardcoreEffective.value = bridge.nativeGetHardcore() && getUserInfo() != null
    }

    override suspend fun loginWithPassword(
        username: String,
        password: String,
    ): Result<RaUserInfo> =
        withContext(Dispatchers.IO) {
            if (!isModuleLoaded()) return@withContext Result.failure(IllegalStateException("RA module not loaded"))
            val (code, err) = awaitAsync { tok -> bridge.nativeLoginWithPassword(tok, username, password) }
            if (code == 0) {
                val info =
                    getUserInfo()
                        ?: return@withContext Result.failure(IllegalStateException("Login succeeded without user info"))
                _isHardcoreEffective.value = bridge.nativeGetHardcore()
                Result.success(info)
            } else {
                Result.failure(RaApiException(code, err.ifBlank { "Login failed ($code)" }))
            }
        }

    override suspend fun loginWithToken(
        username: String,
        token: String,
    ): Result<RaUserInfo> =
        withContext(Dispatchers.IO) {
            if (!isModuleLoaded()) return@withContext Result.failure(IllegalStateException("RA module not loaded"))
            val (code, err) = awaitAsync { tok -> bridge.nativeLoginWithToken(tok, username, token) }
            if (code == 0) {
                val info =
                    getUserInfo()
                        ?: return@withContext Result.failure(
                            IllegalStateException("Token login succeeded without user info"),
                        )
                _isHardcoreEffective.value = bridge.nativeGetHardcore()
                Result.success(info)
            } else {
                Result.failure(RaApiException(code, err.ifBlank { "Token login failed ($code)" }))
            }
        }

    override fun logout() {
        if (isModuleLoaded()) bridge.nativeLogout()
        _activeGame.value = null
        _activeSummary.value = null
        _richPresence.value = ""
        _isHardcoreEffective.value = false
    }

    override fun getUserInfo(): RaUserInfo? {
        if (!isModuleLoaded()) return null
        val raw = bridge.nativeGetUserInfo() ?: return null
        if (raw.size < 8) return null
        return RaUserInfo(
            username = raw[0],
            displayName = raw[1],
            token = raw[2],
            avatarUrl = raw[3],
            score = raw[4].toIntOrNull() ?: 0,
            scoreSoftcore = raw[5].toIntOrNull() ?: 0,
            numUnreadMessages = raw[6].toIntOrNull() ?: 0,
            avatarLastUpdated = raw[7].toLongOrNull() ?: 0L,
        )
    }

    override fun isSystemAllowed(
        libraryName: String?,
        consoleId: Int,
    ): Boolean = if (isModuleLoaded()) bridge.nativeIsSystemAllowed(libraryName, consoleId) else true

    override fun isSettingAllowed(
        libraryName: String?,
        consoleId: Int,
        settingKey: String,
        settingValue: String,
    ): Boolean =
        if (isModuleLoaded()) bridge.nativeIsSettingAllowed(libraryName, consoleId, settingKey, settingValue) else true

    override suspend fun identifyAndLoadGame(
        consoleId: Int,
        filePath: String,
        data: ByteArray?,
        fileOpenDelegate: RaFileOpenDelegate?,
    ): Result<RaGameInfo> =
        withContext(Dispatchers.IO) {
            if (!isModuleLoaded()) return@withContext Result.failure(IllegalStateException("RA module not loaded"))
            bridge.activeFileOpenDelegate = fileOpenDelegate
            val (code, err) =
                awaitAsync { tok ->
                    bridge.nativeBeginIdentifyAndLoadGame(tok, consoleId, filePath, data)
                }
            if (code == 0) {
                val info =
                    refreshGameSnapshots()
                        ?: return@withContext Result.failure(IllegalStateException("Game loaded without info"))
                Result.success(info)
            } else {
                _activeGame.value = null
                _activeSummary.value = null
                _richPresence.value = ""
                Result.failure(RaApiException(code, err.ifBlank { "Game identify failed ($code)" }))
            }
        }

    override suspend fun loadGameByHash(hash: String): Result<RaGameInfo> =
        withContext(Dispatchers.IO) {
            if (!isModuleLoaded()) return@withContext Result.failure(IllegalStateException("RA module not loaded"))
            val (code, err) = awaitAsync { tok -> bridge.nativeBeginLoadGameHash(tok, hash) }
            if (code == 0) {
                val info =
                    refreshGameSnapshots()
                        ?: return@withContext Result.failure(IllegalStateException("Game loaded without info"))
                Result.success(info)
            } else {
                Result.failure(RaApiException(code, err.ifBlank { "Game load failed ($code)" }))
            }
        }

    override suspend fun identifyAndChangeMedia(
        filePath: String,
        data: ByteArray?,
        fileOpenDelegate: RaFileOpenDelegate?,
    ): Result<RaGameInfo> =
        withContext(Dispatchers.IO) {
            if (!isModuleLoaded()) return@withContext Result.failure(IllegalStateException("RA module not loaded"))
            bridge.activeFileOpenDelegate = fileOpenDelegate
            val (code, err) =
                awaitAsync { tok ->
                    bridge.nativeBeginIdentifyAndChangeMedia(tok, filePath, data)
                }
            if (code == 0) {
                val info =
                    refreshGameSnapshots()
                        ?: return@withContext Result.failure(IllegalStateException("Disc switch failed"))
                Result.success(info)
            } else {
                Result.failure(RaApiException(code, err.ifBlank { "Disc switch failed ($code)" }))
            }
        }

    override fun unloadGame() {
        bridge.activeFileOpenDelegate = null
        if (isModuleLoaded()) bridge.nativeUnloadGame()
        _activeGame.value = null
        _activeSummary.value = null
        _richPresence.value = ""
        _isDisconnected.value = false
    }

    override fun attachToLibretroSession(consoleId: Int) {
        sessionAttached.set(true)
        if (isModuleLoaded()) {
            bridge.nativeAttachLibretroSession(consoleId)
        }
    }

    override fun detachFromLibretroSession() {
        sessionAttached.set(false)
        if (isModuleLoaded()) {
            bridge.nativeDetachLibretroSession()
        }
    }

    override fun refreshMemoryMap(consoleId: Int) {
        if (isModuleLoaded()) bridge.nativeRefreshMemoryMap(consoleId)
    }

    override fun dumpMemoryRegions(): String =
        if (isModuleLoaded()) bridge.nativeDumpMemoryRegions() else "RA module not loaded"

    override fun idle() {
        if (!isModuleLoaded()) return
        bridge.nativeIdle()
        _richPresence.value = bridge.nativeGetRichPresence()
    }

    override fun onGameReset() {
        if (isModuleLoaded()) bridge.nativeReset()
    }

    override fun canPause(): RaPauseDecision {
        if (!isModuleLoaded()) return RaPauseDecision.Allowed
        val remaining = bridge.nativeCanPause()
        return if (remaining < 0) RaPauseDecision.Allowed else RaPauseDecision.Cooldown(remaining)
    }

    override fun serializeProgress(): ByteArray? = if (isModuleLoaded()) bridge.nativeSerializeProgress() else null

    override fun deserializeProgress(data: ByteArray): Boolean =
        if (isModuleLoaded()) bridge.nativeDeserializeProgress(data) else false

    override fun getAchievements(
        category: Int,
        grouping: Int,
    ): List<RaAchievementBucket> {
        if (!isModuleLoaded()) return emptyList()
        val rows = bridge.nativeGetAchievements(category, grouping) ?: return emptyList()
        val bucketOrder = ArrayList<Triple<Int, Int, String>>()
        val grouped = LinkedHashMap<Triple<Int, Int, String>, MutableList<RaAchievement>>()
        for (row in rows) {
            if (row.size < 20) continue
            val key = Triple(row[0].toIntOrNull() ?: 0, row[1].toIntOrNull() ?: 0, row[2])
            val list =
                grouped.getOrPut(key) {
                    bucketOrder.add(key)
                    ArrayList()
                }
            list.add(
                RaAchievement(
                    id = row[3].toIntOrNull() ?: 0,
                    title = row[4],
                    description = row[5],
                    points = row[6].toIntOrNull() ?: 0,
                    unlockTime = row[7].toLongOrNull() ?: 0L,
                    measuredPercent = row[8].toFloatOrNull() ?: 0f,
                    measuredProgress = row[9],
                    rarity = row[10].toFloatOrNull() ?: 0f,
                    rarityHardcore = row[11].toFloatOrNull() ?: 0f,
                    state = row[12].toIntOrNull() ?: 0,
                    category = row[13].toIntOrNull() ?: 0,
                    bucket = row[14].toIntOrNull() ?: 0,
                    unlocked = row[15].toIntOrNull() ?: 0,
                    type = row[16].toIntOrNull() ?: 0,
                    badgeName = row[17],
                    badgeUrl = row[18],
                    badgeLockedUrl = row[19],
                ),
            )
        }
        return bucketOrder.map { key ->
            RaAchievementBucket(
                subsetId = key.first,
                bucketType = key.second,
                label = key.third,
                achievements = grouped[key].orEmpty(),
            )
        }
    }

    override fun getLeaderboards(grouping: Int): List<RaLeaderboardBucket> {
        if (!isModuleLoaded()) return emptyList()
        val rows = bridge.nativeGetLeaderboards(grouping) ?: return emptyList()
        val bucketOrder = ArrayList<Triple<Int, Int, String>>()
        val grouped = LinkedHashMap<Triple<Int, Int, String>, MutableList<RaLeaderboard>>()
        for (row in rows) {
            if (row.size < 10) continue
            val key = Triple(row[0].toIntOrNull() ?: 0, row[1].toIntOrNull() ?: 0, row[2])
            val list =
                grouped.getOrPut(key) {
                    bucketOrder.add(key)
                    ArrayList()
                }
            list.add(
                RaLeaderboard(
                    id = row[3].toIntOrNull() ?: 0,
                    title = row[4],
                    description = row[5],
                    trackerValue = row[6],
                    state = row[7].toIntOrNull() ?: 0,
                    format = row[8].toIntOrNull() ?: 0,
                    lowerIsBetter = row[9] == "1",
                ),
            )
        }
        return bucketOrder.map { key ->
            RaLeaderboardBucket(
                subsetId = key.first,
                bucketType = key.second,
                label = key.third,
                leaderboards = grouped[key].orEmpty(),
            )
        }
    }

    override fun getLoadedLibraryName(): String? =
        if (bridge.ensureBridgeLoaded()) bridge.nativeGetLibraryName() else null

    override fun getLoadedLibraryVersion(): String? =
        if (bridge.ensureBridgeLoaded()) bridge.nativeGetLibraryVersion() else null

    override fun onAsyncCompleted(
        token: Long,
        result: Int,
        errorMessage: String,
    ) {
        pendingAsync.remove(token)?.complete(result to errorMessage)
    }

    override fun onEvent(
        type: Int,
        primaryId: Int,
        points: Int,
        newRank: Int,
        numEntries: Int,
        errCode: Int,
        title: String,
        description: String,
        badgeUrl: String,
        measured: String,
        extra1: String,
        extra2: String,
    ) {
        val mapped =
            when (type) {
                1 -> {
                    refreshSummarySnapshot()
                    RetroAchievementsEvent.AchievementTriggered(
                        RaAchievement(
                            id = primaryId,
                            title = title,
                            description = description,
                            points = points,
                            unlockTime = System.currentTimeMillis() / 1000L,
                            measuredPercent = 100f,
                            measuredProgress = measured,
                            rarity = 0f,
                            rarityHardcore = 0f,
                            state = 2,
                            category = 1,
                            bucket = 2,
                            unlocked = if (_isHardcoreEffective.value) 2 else 1,
                            type = 0,
                            badgeName = "",
                            badgeUrl = badgeUrl,
                            badgeLockedUrl = badgeUrl,
                        ),
                    )
                }
                2 ->
                    RetroAchievementsEvent.LeaderboardStarted(
                        RaLeaderboard(primaryId, title, description, extra1, 2, 0, false),
                    )
                3 ->
                    RetroAchievementsEvent.LeaderboardFailed(
                        RaLeaderboard(primaryId, title, description, extra1, 1, 0, false),
                    )
                4 ->
                    RetroAchievementsEvent.LeaderboardSubmitted(
                        RaLeaderboard(primaryId, title, description, extra1, 1, 0, false),
                    )
                5 ->
                    RetroAchievementsEvent.ChallengeIndicatorShow(
                        simpleAch(primaryId, points, title, description, badgeUrl, measured),
                    )
                6 ->
                    RetroAchievementsEvent.ChallengeIndicatorHide(
                        simpleAch(primaryId, points, title, description, badgeUrl, measured),
                    )
                7 ->
                    RetroAchievementsEvent.ProgressIndicatorShow(
                        simpleAch(primaryId, points, title, description, badgeUrl, measured),
                    )
                8 ->
                    RetroAchievementsEvent.ProgressIndicatorHide(
                        simpleAch(primaryId, points, title, description, badgeUrl, measured),
                    )
                9 ->
                    RetroAchievementsEvent.ProgressIndicatorUpdate(
                        simpleAch(primaryId, points, title, description, badgeUrl, measured),
                    )
                10 -> RetroAchievementsEvent.LeaderboardTrackerShow(RaLeaderboardTracker(primaryId, extra1))
                11 -> RetroAchievementsEvent.LeaderboardTrackerHide(RaLeaderboardTracker(primaryId, extra1))
                12 -> RetroAchievementsEvent.LeaderboardTrackerUpdate(RaLeaderboardTracker(primaryId, extra1))
                13 ->
                    RetroAchievementsEvent.LeaderboardScoreboard(
                        RaLeaderboardScoreboard(primaryId, extra1, extra2, newRank, numEntries, emptyList()),
                    )
                14 -> RetroAchievementsEvent.ResetRequired
                15 -> RetroAchievementsEvent.GameCompleted
                16 -> RetroAchievementsEvent.ServerError(RaServerError(errCode, primaryId, description, title))
                17 -> {
                    _isDisconnected.value = true
                    RetroAchievementsEvent.Disconnected
                }
                18 -> {
                    _isDisconnected.value = false
                    RetroAchievementsEvent.Reconnected
                }
                19 ->
                    RetroAchievementsEvent.SubsetCompleted(
                        RaSubset(primaryId, title, "", badgeUrl, points, 0),
                    )
                else -> null
            }
        if (mapped != null) {
            _events.tryEmit(mapped)
        }
        if (isModuleLoaded()) {
            _richPresence.value = bridge.nativeGetRichPresence()
        }
    }

    private fun refreshGameSnapshots(): RaGameInfo? {
        val gRaw = bridge.nativeGetGameInfo() ?: return null
        if (gRaw.size < 6) return null
        val info =
            RaGameInfo(
                id = gRaw[0].toIntOrNull() ?: 0,
                consoleId = gRaw[1].toIntOrNull() ?: 0,
                title = gRaw[2],
                hash = gRaw[3],
                badgeName = gRaw[4],
                badgeUrl = gRaw[5],
            )
        _activeGame.value = info
        refreshSummarySnapshot()
        _richPresence.value = bridge.nativeGetRichPresence()
        _isHardcoreEffective.value = bridge.nativeGetHardcore() && getUserInfo() != null
        return info
    }

    private fun refreshSummarySnapshot() {
        val sRaw = bridge.nativeGetUserGameSummary() ?: return
        if (sRaw.size < 8) return
        _activeSummary.value =
            RaUserGameSummary(
                numCoreAchievements = sRaw[0].toInt(),
                numUnofficialAchievements = sRaw[1].toInt(),
                numUnlockedAchievements = sRaw[2].toInt(),
                numUnsupportedAchievements = sRaw[3].toInt(),
                pointsCore = sRaw[4].toInt(),
                pointsUnlocked = sRaw[5].toInt(),
                beatenTime = sRaw[6],
                completedTime = sRaw[7],
            )
    }

    private suspend fun awaitAsync(dispatch: (Long) -> Unit): Pair<Int, String> {
        val tok = nextToken.getAndIncrement()
        val deferred = CompletableDeferred<Pair<Int, String>>()
        pendingAsync[tok] = deferred
        try {
            dispatch(tok)
            return deferred.await()
        } catch (t: Throwable) {
            pendingAsync.remove(tok)
            throw t
        }
    }

    private fun failAllPendingAsync(reason: String) {
        val it = pendingAsync.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            it.remove()
            entry.value.complete(-25 to reason)
        }
    }

    private fun simpleAch(
        id: Int,
        points: Int,
        title: String,
        desc: String,
        badgeUrl: String,
        measured: String,
    ) = RaAchievement(
        id = id,
        title = title,
        description = desc,
        points = points,
        unlockTime = 0L,
        measuredPercent = 0f,
        measuredProgress = measured,
        rarity = 0f,
        rarityHardcore = 0f,
        state = 1,
        category = 1,
        bucket = 1,
        unlocked = 0,
        type = 0,
        badgeName = "",
        badgeUrl = badgeUrl,
        badgeLockedUrl = badgeUrl,
    )

    companion object {
        const val SUPPORTED_ABI_MAJOR = 1
    }
}

class RaApiException(
    val code: Int,
    override val message: String,
) : Exception(message)
