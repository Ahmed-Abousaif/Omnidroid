package com.omnidroid.app.shared.ra

import android.content.Context
import android.content.SharedPreferences
import com.omnidroid.BuildConfig
import com.omnidroid.app.mobile.feature.settings.SettingsManager
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.DataFile
import com.omnidroid.lib.library.db.entity.RaGameCache
import com.omnidroid.lib.ra.HardcorePolicy
import com.omnidroid.lib.ra.RaCredentialStore
import com.omnidroid.lib.ra.RaGameInfo
import com.omnidroid.lib.ra.RaInstalledModule
import com.omnidroid.lib.ra.RaModuleDeliveryStatus
import com.omnidroid.lib.ra.RaModuleLoadStatus
import com.omnidroid.lib.ra.RaModuleManager
import com.omnidroid.lib.ra.RaUpdateCheckOutcome
import com.omnidroid.lib.ra.RaUserInfo
import com.omnidroid.lib.ra.RetroAchievementsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Singleton

/**
 * Process-scoped coordinator bridging `libomnidroid_ra.so` loading, key-backed token sign-in,
 * settings synchronization, and Room game-cache persistence.
 */
@Singleton
class RetroAchievementsCoordinator(
    private val context: Context,
    val moduleManager: RaModuleManager,
    val credentialStore: RaCredentialStore,
    val engine: RetroAchievementsEngine,
    val hardcorePolicy: HardcorePolicy,
    private val settingsManager: SettingsManager,
    private val database: RetrogradeDatabase,
    private val sharedPreferences: SharedPreferences,
) {
    private val loadMutex = Mutex()
    private val json = Json { encodeDefaults = true }

    @Volatile
    private var loadedModule: RaInstalledModule? = null

    fun deliveryStatus(): RaModuleDeliveryStatus = moduleManager.getDeliveryStatus()

    fun isSignedIn(): Boolean = credentialStore.hasCredentials() || engine.getUserInfo() != null

    fun savedUsername(): String? = engine.getUserInfo()?.username ?: credentialStore.getSavedUsername()

    fun formatUserAgent(
        moduleVersion: String,
        rcheevosVersion: String,
        coreLabel: String? = null,
        appVersion: String = BuildConfig.VERSION_NAME,
    ): String = formatUserAgentHeader(appVersion, moduleVersion, rcheevosVersion, coreLabel)

    /**
     * Loads packaged or downloaded `libomnidroid_ra.so` if available, optionally fetching it on
     * first use for GitHub builds when missing.
     */
    suspend fun ensureModuleLoaded(
        coreLabel: String? = null,
        downloadIfMissing: Boolean = false,
    ): Result<RaInstalledModule> =
        loadMutex.withLock {
            withContext(Dispatchers.IO) {
                loadedModule?.takeIf { engine.isModuleLoaded() }?.let { existing ->
                    val status = engine.moduleStatus.value
                    val loadedState = status as? RaModuleLoadStatus.Loaded
                    val buildInfo = loadedState?.buildInfo
                    val buildVer = buildInfo?.moduleVersion
                    val modVer = buildVer?.takeIf { it.isNotBlank() } ?: existing.version
                    val rchVer = buildInfo?.rcheevosVersion ?: ""
                    engine.setUserAgent(formatUserAgent(modVer, rchVer, coreLabel))
                    return@withContext Result.success(existing)
                }

                var candidate = moduleManager.resolveLoadableModule()
                if (candidate == null && downloadIfMissing) {
                    moduleManager.checkAndUpdateIfNeeded(force = false)
                    candidate = moduleManager.resolveLoadableModule()
                }
                if (candidate == null) {
                    return@withContext Result.failure(
                        IllegalStateException("RetroAchievements native module is not installed"),
                    )
                }

                tryLoadCandidate(candidate, coreLabel)
            }
        }

    private suspend fun tryLoadCandidate(
        candidate: RaInstalledModule,
        coreLabel: String?,
    ): Result<RaInstalledModule> {
        val res = engine.loadModule(candidate.file.absolutePath)
        return res.fold(
            onSuccess = { buildInfo ->
                moduleManager.markNativeInitSucceeded(candidate, buildInfo)
                engine.setUserAgent(
                    formatUserAgent(
                        buildInfo.moduleVersion.ifBlank { candidate.version },
                        buildInfo.rcheevosVersion,
                        coreLabel,
                    ),
                )
                engine.applySettings(settingsManager.raSettings())
                loadedModule = candidate
                Result.success(candidate)
            },
            onFailure = { err ->
                val reason = err.message ?: "Native RA load failed"
                moduleManager.markNativeInitFailed(candidate, reason)
                Timber.e(err, "Failed to load RA module %s", candidate.version)
                Result.failure(err)
            },
        )
    }

    suspend fun loginWithPassword(
        username: String,
        password: String,
    ): Result<RaUserInfo> {
        val loaded = ensureModuleLoaded(downloadIfMissing = true)
        if (loaded.isFailure) {
            return Result.failure(loaded.exceptionOrNull() ?: IllegalStateException("RA module missing"))
        }
        val loginRes = engine.loginWithPassword(username.trim(), password)
        loginRes.onSuccess { user ->
            credentialStore.saveToken(user.username, user.token)
            val keyEnabled = context.getString(com.omnidroid.R.string.pref_key_ra_enabled)
            if (!sharedPreferences.getBoolean(keyEnabled, false)) {
                sharedPreferences.edit().putBoolean(keyEnabled, true).apply()
            }
        }
        return loginRes
    }

    suspend fun silentLoginIfNeeded(coreLabel: String? = null): Result<RaUserInfo>? {
        val creds = credentialStore.loadToken() ?: return null
        val loaded = ensureModuleLoaded(coreLabel = coreLabel, downloadIfMissing = false)
        if (loaded.isFailure) {
            return Result.failure(loaded.exceptionOrNull() ?: IllegalStateException("RA module missing"))
        }
        val existing = engine.getUserInfo()
        if (existing != null && existing.username.equals(creds.username, ignoreCase = true)) {
            return Result.success(existing)
        }
        val res = engine.loginWithToken(creds.username, creds.token)
        res.onSuccess { user ->
            if (user.token.isNotBlank() && user.token != creds.token) {
                credentialStore.saveToken(user.username, user.token)
            }
        }
        return res
    }

    fun logout() {
        runCatching { engine.logout() }
        credentialStore.clear()
        hardcorePolicy.clearSession()
    }

    suspend fun checkForModuleUpdates(force: Boolean): RaUpdateCheckOutcome =
        moduleManager.checkAndUpdateIfNeeded(force = force)

    suspend fun syncSettingsToEngine() {
        if (!engine.isModuleLoaded()) return
        engine.applySettings(settingsManager.raSettings())
    }

    suspend fun setHardcorePreference(enabled: Boolean) {
        val key = context.getString(com.omnidroid.R.string.pref_key_ra_hardcore)
        sharedPreferences.edit().putBoolean(key, enabled).apply()
        if (engine.isModuleLoaded()) {
            engine.applySettings(settingsManager.raSettings().copy(hardcore = enabled))
        }
    }

    suspend fun dataFilesForGame(gameId: Int): List<DataFile> =
        withContext(Dispatchers.IO) {
            database.dataFileDao().selectDataFilesForGame(gameId)
        }

    suspend fun persistGameCache(
        gameId: Int,
        gameInfo: RaGameInfo,
    ) = withContext(Dispatchers.IO) {
        val summary = engine.activeSummary.value
        val achievements = engine.getAchievements()
        val leaderboards = engine.getLeaderboards()
        val rp = engine.richPresence.value
        val entry =
            RaGameCache(
                gameId = gameId,
                raGameId = gameInfo.id,
                consoleId = gameInfo.consoleId,
                raHash = gameInfo.hash,
                title = gameInfo.title,
                badgeUrl = gameInfo.badgeUrl,
                numCoreAchievements = summary?.numCoreAchievements ?: 0,
                numUnlockedAchievements = summary?.numUnlockedAchievements ?: 0,
                pointsCore = summary?.pointsCore ?: 0,
                pointsUnlocked = summary?.pointsUnlocked ?: 0,
                beatenTime = summary?.beatenTime ?: 0L,
                completedTime = summary?.completedTime ?: 0L,
                richPresence = rp,
                achievementsJson = json.encodeToString(achievements),
                leaderboardsJson = json.encodeToString(leaderboards),
                updatedAt = System.currentTimeMillis(),
            )
        database.raGameCacheDao().upsert(entry)
    }

    companion object {
        internal fun formatUserAgentHeader(
            appVersion: String,
            moduleVersion: String,
            rcheevosVersion: String,
            coreLabel: String? = null,
        ): String {
            val cleanAppVer = appVersion.substringBefore('-').ifBlank { "0" }
            val modVer = moduleVersion.ifBlank { "0" }
            val rchVer = rcheevosVersion.ifBlank { "12.5.0" }
            val platformClause = if (coreLabel.isNullOrBlank()) "(Android)" else "(Android; $coreLabel)"
            return "Omnidroid/$cleanAppVer $platformClause omnidroid_ra/$modVer rcheevos/$rchVer"
        }
    }
}
