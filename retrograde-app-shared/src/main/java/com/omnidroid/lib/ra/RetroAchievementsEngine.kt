package com.omnidroid.lib.ra

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

fun interface RaFileOpenDelegate {
    fun openFd(path: String): ParcelFileDescriptor?
}

interface RetroAchievementsEngine {
    val events: SharedFlow<RetroAchievementsEvent>
    val moduleStatus: StateFlow<RaModuleLoadStatus>
    val activeGame: StateFlow<RaGameInfo?>
    val activeSummary: StateFlow<RaUserGameSummary?>
    val richPresence: StateFlow<String>
    val isHardcoreEffective: StateFlow<Boolean>
    val isDisconnected: StateFlow<Boolean>

    suspend fun loadModule(verifiedPath: String): Result<RaModuleBuildInfo>

    fun unloadModule()

    fun isModuleLoaded(): Boolean

    fun setUserAgent(userAgent: String)

    fun applySettings(settings: RetroAchievementsSettings)

    suspend fun loginWithPassword(
        username: String,
        password: String,
    ): Result<RaUserInfo>

    suspend fun loginWithToken(
        username: String,
        token: String,
    ): Result<RaUserInfo>

    fun logout()

    fun getUserInfo(): RaUserInfo?

    fun isSystemAllowed(
        libraryName: String?,
        consoleId: Int,
    ): Boolean

    fun isSettingAllowed(
        libraryName: String?,
        consoleId: Int,
        settingKey: String,
        settingValue: String,
    ): Boolean

    suspend fun identifyAndLoadGame(
        consoleId: Int,
        filePath: String,
        data: ByteArray? = null,
        fileOpenDelegate: RaFileOpenDelegate? = null,
    ): Result<RaGameInfo>

    suspend fun loadGameByHash(hash: String): Result<RaGameInfo>

    suspend fun identifyAndChangeMedia(
        filePath: String,
        data: ByteArray? = null,
        fileOpenDelegate: RaFileOpenDelegate? = null,
    ): Result<RaGameInfo>

    fun unloadGame()

    fun attachToLibretroSession(consoleId: Int)

    fun detachFromLibretroSession()

    fun refreshMemoryMap(consoleId: Int)

    fun dumpMemoryRegions(): String

    fun idle()

    fun onGameReset()

    fun canPause(): RaPauseDecision

    fun serializeProgress(): ByteArray?

    fun deserializeProgress(data: ByteArray): Boolean

    fun getAchievements(
        category: Int = ACHIEVEMENT_CATEGORY_CORE_AND_UNOFFICIAL,
        grouping: Int = ACHIEVEMENT_GROUPING_PROGRESS,
    ): List<RaAchievementBucket>

    fun getLeaderboards(grouping: Int = LEADERBOARD_GROUPING_NONE): List<RaLeaderboardBucket>

    fun getLoadedLibraryName(): String?

    fun getLoadedLibraryVersion(): String?

    companion object {
        const val ACHIEVEMENT_CATEGORY_CORE = 1
        const val ACHIEVEMENT_CATEGORY_UNOFFICIAL = 2
        const val ACHIEVEMENT_CATEGORY_CORE_AND_UNOFFICIAL = 3

        const val ACHIEVEMENT_GROUPING_LOCK_STATE = 0
        const val ACHIEVEMENT_GROUPING_PROGRESS = 1

        const val LEADERBOARD_GROUPING_NONE = 0
        const val LEADERBOARD_GROUPING_TRACKING = 1
    }
}

class NoopRetroAchievementsEngine : RetroAchievementsEngine {
    private val _events = MutableSharedFlow<RetroAchievementsEvent>(extraBufferCapacity = 8)
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
        Result.failure(UnsupportedOperationException("NoopRetroAchievementsEngine cannot load native module"))

    override fun unloadModule() = Unit

    override fun isModuleLoaded(): Boolean = false

    override fun setUserAgent(userAgent: String) = Unit

    override fun applySettings(settings: RetroAchievementsSettings) = Unit

    override suspend fun loginWithPassword(
        username: String,
        password: String,
    ): Result<RaUserInfo> = Result.failure(IllegalStateException("RetroAchievements module not loaded"))

    override suspend fun loginWithToken(
        username: String,
        token: String,
    ): Result<RaUserInfo> = Result.failure(IllegalStateException("RetroAchievements module not loaded"))

    override fun logout() = Unit

    override fun getUserInfo(): RaUserInfo? = null

    override fun isSystemAllowed(
        libraryName: String?,
        consoleId: Int,
    ): Boolean = true

    override fun isSettingAllowed(
        libraryName: String?,
        consoleId: Int,
        settingKey: String,
        settingValue: String,
    ): Boolean = true

    override suspend fun identifyAndLoadGame(
        consoleId: Int,
        filePath: String,
        data: ByteArray?,
        fileOpenDelegate: RaFileOpenDelegate?,
    ): Result<RaGameInfo> = Result.failure(IllegalStateException("RetroAchievements module not loaded"))

    override suspend fun loadGameByHash(hash: String): Result<RaGameInfo> =
        Result.failure(IllegalStateException("RetroAchievements module not loaded"))

    override suspend fun identifyAndChangeMedia(
        filePath: String,
        data: ByteArray?,
        fileOpenDelegate: RaFileOpenDelegate?,
    ): Result<RaGameInfo> = Result.failure(IllegalStateException("RetroAchievements module not loaded"))

    override fun unloadGame() = Unit

    override fun attachToLibretroSession(consoleId: Int) = Unit

    override fun detachFromLibretroSession() = Unit

    override fun refreshMemoryMap(consoleId: Int) = Unit

    override fun dumpMemoryRegions(): String = "RetroAchievements unavailable (noop)"

    override fun idle() = Unit

    override fun onGameReset() = Unit

    override fun canPause(): RaPauseDecision = RaPauseDecision.Allowed

    override fun serializeProgress(): ByteArray? = null

    override fun deserializeProgress(data: ByteArray): Boolean = false

    override fun getAchievements(
        category: Int,
        grouping: Int,
    ): List<RaAchievementBucket> = emptyList()

    override fun getLeaderboards(grouping: Int): List<RaLeaderboardBucket> = emptyList()

    override fun getLoadedLibraryName(): String? = null

    override fun getLoadedLibraryVersion(): String? = null
}
