package com.omnidroid.app.shared.game

import android.app.Activity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.omnidroid.R
import com.omnidroid.app.OmnidroidApplication
import com.omnidroid.app.mobile.feature.settings.SettingsManager
import com.omnidroid.app.mobile.shared.compose.dialog.DialogActionRole
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogAction
import com.omnidroid.app.mobile.shared.compose.dialog.showOmnidroidDialog
import com.omnidroid.app.shared.cast.CastDisplayManager
import com.omnidroid.app.shared.game.backend.RequirementPickerLauncherActivity
import com.omnidroid.app.shared.input.InputDeviceManager
import com.omnidroid.app.shared.main.GameLaunchTaskHandler
import com.omnidroid.app.shared.savesync.PreGameSyncActivity
import com.omnidroid.app.tv.shared.TVHelper
import com.omnidroid.common.displayToast
import com.omnidroid.lib.backend.ConsoleBackendRegistry
import com.omnidroid.lib.backend.RequirementDescriptor
import com.omnidroid.lib.backend.RequirementItem
import com.omnidroid.lib.backend.RequirementSeverity
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

        isExternalLaunch: Boolean = false,

        onLaunched: (() -> Unit)? = null,

    ): Boolean {

        val system = GameSystem.findById(game.systemId)

        val systemDescriptor = ConsoleBackendRegistry.findDescriptorForSystem(game.systemId)

        val initialConfig = system.systemCoreConfigs.firstOrNull()
        val initialReqs = initialConfig?.let { systemDescriptor?.requirementsFor(it) }
        val initialUnfulfilled = initialReqs?.items?.filter { it.severity == RequirementSeverity.REQUIRED && !it.isFulfilled(activity) }

        if (!initialUnfulfilled.isNullOrEmpty()) {

            showMissingRequirementsDialog(activity, initialReqs, initialUnfulfilled)

            return false

        }

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

            val coreConfig = coresSelection.getCoreConfigForSystem(system)

            val descriptor = ConsoleBackendRegistry.findDescriptorForCore(coreConfig.coreID)

            val coreReqs = descriptor?.requirementsFor(coreConfig)

            val coreUnfulfilled = coreReqs?.items?.filter { it.severity == RequirementSeverity.REQUIRED && !it.isFulfilled(activity) }

            if (!coreUnfulfilled.isNullOrEmpty()) {

                showMissingRequirementsDialog(activity, coreReqs, coreUnfulfilled)

                return@launch

            }

            val shouldPreSync =

                saveSyncManager.isSupported() &&

                    saveSyncManager.isConfigured() &&

                    settingsManager.syncBeforeGame() &&

                    gameCloudSyncPreferences.shouldSyncGame(game, settingsManager.syncSaves())

            if (shouldPreSync) {

                activity.startActivityForResult(

                    PreGameSyncActivity.intent(activity, game, coreConfig, loadSave, leanback, isExternalLaunch),

                    BaseGameActivity.REQUEST_PLAY_GAME,

                )

                onLaunched?.invoke()

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

                    isExternalLaunch,

                )

                onLaunched?.invoke()

            }

        }

        return true

    }

    private fun showMissingRequirementsDialog(

        activity: Activity,

        descriptor: RequirementDescriptor,

        unfulfilled: List<RequirementItem>,

    ) {

        if (activity.isFinishing || activity.isDestroyed) return

        val item = unfulfilled.firstOrNull() ?: return

        showOmnidroidDialog(
            activity = activity,
            title = descriptor.title,
            message = item.description,
            actions = listOf(
                OmnidroidDialogAction(activity.getString(android.R.string.cancel), DialogActionRole.Cancel),
                OmnidroidDialogAction(activity.getString(R.string.switch_keys_import_action)) {
                    item.action?.let { RequirementPickerLauncherActivity.launch(activity, it) }
                },
            ),
        )

    }

}

