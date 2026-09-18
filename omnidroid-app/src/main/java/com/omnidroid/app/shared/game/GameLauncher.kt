package com.omnidroid.app.shared.game

import android.app.Activity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.omnidroid.R
import com.omnidroid.app.OmnidroidApplication
import com.omnidroid.app.mobile.feature.settings.SettingsManager
import com.omnidroid.app.shared.cast.CastDisplayManager
import com.omnidroid.app.shared.input.InputDeviceManager
import com.omnidroid.app.shared.main.GameLaunchTaskHandler
import com.omnidroid.app.shared.savesync.PreGameSyncActivity
import com.omnidroid.app.tv.shared.TVHelper
import com.omnidroid.common.displayToast
import com.omnidroid.lib.core.CoresSelection
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.savesync.GameCloudSyncPreferences
import com.omnidroid.lib.savesync.SaveSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
            (activity as? LifecycleOwner)?.lifecycleScope
                ?: OmnidroidApplication.scope(activity)

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
