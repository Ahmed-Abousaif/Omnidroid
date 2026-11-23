package com.omnidroid.app.shared.game.backend.ps3

import android.app.Activity
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import com.omnidroid.app.shared.ps3.Armsx3Bridge
import com.omnidroid.lib.backend.ConsoleBackendRuntime
import com.omnidroid.lib.backend.ps3.Ps3BackendDescriptor
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Ps3BackendRuntime : ConsoleBackendRuntime {
    override suspend fun prepare(activity: Activity, game: Game, config: SystemCoreConfig): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = android.net.Uri.parse(game.fileUri)
                val info = Armsx3Bridge.withDataAccess(activity) {
                    Armsx3Bridge.initialize(activity.applicationContext)
                    com.omnidroid.lib.library.scan.Ps3Content.read(activity, uri) ?: Armsx3Bridge.probe(activity, uri)
                }
                val saved = dependencies(activity).savesManager().getSaveRAM(game, config)
                Armsx3Bridge.withDataAccess(activity) {
                    if (saved != null) com.omnidroid.lib.saves.Ps3SaveArchive.restore(
                        java.io.File(Armsx3Bridge.baseDir(activity), "config/dev_hdd0/home/00000001/savedata"), info.titleId, saved,
                    )
                    Armsx3Bridge.initialize(activity.applicationContext)
                }
            }
        }

    override fun createSession(activity: Activity, game: Game, config: SystemCoreConfig, container: ViewGroup, onExit: (Int) -> Unit) =
        Ps3BackendSession(activity, game, PreferenceManager.getDefaultSharedPreferences(activity), Ps3BackendDescriptor().capabilities(config), dependencies(activity).savesManager(), onExit)

    private fun dependencies(activity: Activity) = dagger.hilt.android.EntryPointAccessors.fromApplication(activity, Ps3RuntimeDependencies::class.java)
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface Ps3RuntimeDependencies {
    fun savesManager(): com.omnidroid.lib.saves.SavesManager
}
