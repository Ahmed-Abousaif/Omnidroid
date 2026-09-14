package com.swordfish.lemuroid.app.shared.game

import android.app.Activity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.shared.cast.CastDisplayManager
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.lemuroid.app.shared.savesync.PreGameSyncActivity
import com.swordfish.lemuroid.app.tv.shared.TVHelper
import com.swordfish.lemuroid.common.displayToast
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.savesync.GameCloudSyncPreferences
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class GameLauncher(
    private val coresSelection: CoresSelection,
    private val gameLaunchTaskHandler: GameLaunchTaskHandler,
    private val saveSyncManager: SaveSyncManager,
    private val settingsManager: SettingsManager,
    private val gameCloudSyncPreferences: GameCloudSyncPreferences,
    private val castDisplayManager: CastDisplayManager,
    private val inputDeviceManager: InputDeviceManager,
) {
    fun launchGameAsync(
        activity: Activity,
        game: Game,
        loadSave: Boolean,
        leanback: Boolean,
    ): Boolean {
        if (GameProcessLock.isHeldByAnotherProcess(activity.applicationContext)) {
            activity.displayToast(R.string.game_process_another_game_running)
            return false
        }

        if (TVHelper.isTV(activity) && !inputDeviceManager.hasEnabledGamePad()) {
            activity.displayToast(R.string.tv_game_message_missing_gamepad)
            return false
        }

        val scope: CoroutineScope =
            (activity as? LifecycleOwner)?.lifecycleScope ?: GlobalScope

        scope.launch(Dispatchers.Main) {
            val system = GameSystem.findById(game.systemId)
            val coreConfig = coresSelection.getCoreConfigForSystem(system)
            val shouldPreSync =
                saveSyncManager.isSupported() &&
                    saveSyncManager.isConfigured() &&
                    settingsManager.syncBeforeGame() &&
                    gameCloudSyncPreferences.shouldSyncGame(game, settingsManager.syncSaves())

            if (shouldPreSync) {
                activity.startActivityForResult(
                    PreGameSyncActivity.intent(activity, game, coreConfig, loadSave, leanback),
                    BaseGameActivity.REQUEST_PLAY_GAME,
                )
            } else {
                gameLaunchTaskHandler.handleGameStart(activity.applicationContext)
                castDisplayManager.hideIdleForGame()
                BaseGameActivity.launchGame(
                    activity,
                    coreConfig,
                    game,
                    loadSave,
                    leanback,
                    castDisplayManager.launchOptions(),
                )
            }
        }

        return true
    }
}
