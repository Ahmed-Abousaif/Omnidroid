package com.omnidroid.lib.ra

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class HardcoreDecision {
    data object Allowed : HardcoreDecision()

    data class Blocked(
        val reason: HardcoreBlockReason,
        val detail: String = "",
    ) : HardcoreDecision()
}

enum class HardcoreBlockReason {
    LOAD_SAVE_STATE,
    LOAD_AUTO_SAVE_ON_LAUNCH,
    REWIND,
    SLOW_MOTION,
    CHEATS,
    MEMORY_EDIT,
    DISALLOWED_CORE_OPTION,
}

data class HardcoreSessionState(
    val moduleLoaded: Boolean = false,
    val loggedIn: Boolean = false,
    val raEnabled: Boolean = false,
    val hardcoreRequested: Boolean = true,
    val gameLoaded: Boolean = false,
    val coreSupported: Boolean = false,
) {
    /**
     * Hardcore is NEVER considered active when the RA module is absent or unloaded,
     * when RA is disabled, or when the user is signed out.
     */
    val isHardcoreActive: Boolean
        get() = moduleLoaded && raEnabled && loggedIn && hardcoreRequested && coreSupported
}

/**
 * Single authoritative frontend enforcement point for RetroAchievements Hardcore mode rules.
 * Every entrypoint (save-state menu, hotkeys, launch auto-restore, rewind, slow-motion,
 * cheats, memory editor, and disallowed core options) checks this policy.
 */
class HardcorePolicy(
    private val engine: RetroAchievementsEngine,
) {
    private val _sessionState = MutableStateFlow(HardcoreSessionState())
    val sessionState: StateFlow<HardcoreSessionState> = _sessionState.asStateFlow()

    val isHardcoreActive: Boolean
        get() = _sessionState.value.isHardcoreActive && engine.isModuleLoaded()

    fun updateSession(
        moduleLoaded: Boolean = _sessionState.value.moduleLoaded,
        loggedIn: Boolean = _sessionState.value.loggedIn,
        raEnabled: Boolean = _sessionState.value.raEnabled,
        hardcoreRequested: Boolean = _sessionState.value.hardcoreRequested,
        gameLoaded: Boolean = _sessionState.value.gameLoaded,
        coreSupported: Boolean = _sessionState.value.coreSupported,
    ) {
        _sessionState.value =
            HardcoreSessionState(
                moduleLoaded = moduleLoaded && engine.isModuleLoaded(),
                loggedIn = loggedIn,
                raEnabled = raEnabled,
                hardcoreRequested = hardcoreRequested,
                gameLoaded = gameLoaded,
                coreSupported = coreSupported,
            )
    }

    fun clearSession() {
        _sessionState.value = HardcoreSessionState()
    }

    /** Saving states is permitted in Hardcore; loading any save state is blocked. */
    fun canSaveState(): HardcoreDecision = HardcoreDecision.Allowed

    fun canLoadSaveState(isAutoSaveOnLaunch: Boolean = false): HardcoreDecision {
        if (!isHardcoreActive) return HardcoreDecision.Allowed
        return if (isAutoSaveOnLaunch) {
            HardcoreDecision.Blocked(
                reason = HardcoreBlockReason.LOAD_AUTO_SAVE_ON_LAUNCH,
                detail = "Auto-save restore skipped in Hardcore mode",
            )
        } else {
            HardcoreDecision.Blocked(
                reason = HardcoreBlockReason.LOAD_SAVE_STATE,
                detail = "Loading save states is disabled in Hardcore mode",
            )
        }
    }

    fun canEnableRewind(): HardcoreDecision =
        if (!isHardcoreActive) {
            HardcoreDecision.Allowed
        } else {
            HardcoreDecision.Blocked(
                reason = HardcoreBlockReason.REWIND,
                detail = "Rewind is disabled in Hardcore mode",
            )
        }

    fun canEnableSlowMotion(): HardcoreDecision =
        if (!isHardcoreActive) {
            HardcoreDecision.Allowed
        } else {
            HardcoreDecision.Blocked(
                reason = HardcoreBlockReason.SLOW_MOTION,
                detail = "Slow motion is disabled in Hardcore mode",
            )
        }

    /** Fast-forward is explicitly permitted by RetroAchievements Hardcore rules. */
    fun canEnableFastForward(): HardcoreDecision = HardcoreDecision.Allowed

    fun canSetFrameSpeedMultiplier(multiplier: Float): HardcoreDecision =
        if (multiplier < 1.0f) canEnableSlowMotion() else canEnableFastForward()

    fun canUseCheats(): HardcoreDecision =
        if (!isHardcoreActive) {
            HardcoreDecision.Allowed
        } else {
            HardcoreDecision.Blocked(
                reason = HardcoreBlockReason.CHEATS,
                detail = "Cheats are disabled in Hardcore mode",
            )
        }

    fun canEditMemory(): HardcoreDecision =
        if (!isHardcoreActive) {
            HardcoreDecision.Allowed
        } else {
            HardcoreDecision.Blocked(
                reason = HardcoreBlockReason.MEMORY_EDIT,
                detail = "Memory editing is disabled in Hardcore mode",
            )
        }

    fun canApplyCoreOption(
        libraryName: String?,
        consoleId: Int,
        key: String,
        value: String,
    ): HardcoreDecision {
        if (!isHardcoreActive) return HardcoreDecision.Allowed
        if (!engine.isSettingAllowed(libraryName, consoleId, key, value)) {
            return HardcoreDecision.Blocked(
                reason = HardcoreBlockReason.DISALLOWED_CORE_OPTION,
                detail = "Setting '$key=$value' is not allowed in Hardcore mode",
            )
        }
        return HardcoreDecision.Allowed
    }

    fun canPauseGame(): RaPauseDecision {
        if (!isHardcoreActive) return RaPauseDecision.Allowed
        return engine.canPause()
    }
}
