// Retain the established backend package for existing imports.
@file:Suppress("ktlint:standard:package-name")

package com.omnidroid.app.shared.game.backend.switch_backend

import android.app.Activity
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import com.omnidroid.app.shared.switch_emu.EdenBridge
import com.omnidroid.lib.backend.ConsoleBackendRuntime
import com.omnidroid.lib.backend.switch_backend.SwitchBackendDescriptor
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SwitchBackendRuntime : ConsoleBackendRuntime {
    override suspend fun prepare(
        activity: Activity,
        game: Game,
        config: SystemCoreConfig,
    ): Result<Unit> = withContext(Dispatchers.IO) { runCatching { EdenBridge.initialize(activity.applicationContext) } }

    override fun createSession(
        activity: Activity,
        game: Game,
        config: SystemCoreConfig,
        container: ViewGroup,
        onExit: (Int) -> Unit,
    ) = SwitchBackendSession(
        activity,
        game,
        PreferenceManager.getDefaultSharedPreferences(activity),
        SwitchBackendDescriptor().capabilities(config),
        onExit,
    )
}
