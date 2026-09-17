package com.omnidroid.app.shared.game

import android.app.GameManager
import android.app.GameState
import android.content.Context
import android.os.Build
import timber.log.Timber

/**
 * Reports session state to the platform [GameManager] so OEMs can apply game power/loading
 * policies. Does not declare or implement Game Mode fidelity/FPS tradeoffs.
 */
object GamePlatformSession {
    fun reportLibrary(context: Context) {
        setState(
            context = context,
            isLoading = false,
            mode = GameState.MODE_NONE,
        )
    }

    fun reportLoading(context: Context) {
        setState(
            context = context,
            isLoading = true,
            mode = GameState.MODE_GAMEPLAY_INTERRUPTIBLE,
        )
    }

    fun reportPlaying(context: Context) {
        setState(
            context = context,
            isLoading = false,
            mode = GameState.MODE_GAMEPLAY_INTERRUPTIBLE,
        )
    }

    private fun setState(
        context: Context,
        isLoading: Boolean,
        mode: Int,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val gameManager = context.getSystemService(GameManager::class.java) ?: return
        try {
            gameManager.setGameState(GameState(isLoading, mode))
        } catch (error: Exception) {
            Timber.w(error, "Unable to report game session state")
        }
    }
}
