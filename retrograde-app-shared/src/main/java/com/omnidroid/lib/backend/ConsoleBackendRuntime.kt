package com.omnidroid.lib.backend

import android.app.Activity
import android.view.ViewGroup
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.db.entity.Game

/**
 * Heavy lifecycle and session factory implemented by dynamic feature modules or base adapters.
 */
interface ConsoleBackendRuntime {
    suspend fun prepare(
        activity: Activity,
        game: Game,
        config: SystemCoreConfig,
    ): Result<Unit>

    fun createSession(
        activity: Activity,
        game: Game,
        config: SystemCoreConfig,
        container: ViewGroup,
    ): BackendSession
}

