package com.omnidroid.app.mobile.feature.gamemenu.coreoptions

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Text
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.gamemenu.GameMenuActivity
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.app.shared.coreoptions.CoreOptionsPreferenceHelper
import com.omnidroid.app.shared.coreoptions.OmnidroidCoreOption
import com.omnidroid.app.shared.settings.ControllerConfigsManager
import com.omnidroid.app.utils.android.settings.OmnidroidCardSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsList
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsPage
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSwitch
import com.omnidroid.app.utils.android.settings.booleanPreferenceState
import com.omnidroid.app.utils.android.settings.indexPreferenceState
import com.omnidroid.lib.core.CoreVariablesManager
import kotlinx.coroutines.yield

@Composable
fun GameMenuCoreOptionsScreen(
    viewModel: GameMenuCoreOptionsViewModel,
    gameMenuRequest: GameMenuActivity.GameMenuRequest,
) {
    val context = LocalContext.current

    val connectedGamePads by viewModel.connectedGamePads.collectAsState(0)

    val allOptions =
        remember(gameMenuRequest) {
            gameMenuRequest.coreOptions + gameMenuRequest.advancedCoreOptions
        }

    val firstItemRequester = remember { FocusRequester() }
    val controllerNav = LocalControllerNavigation.current

    DisposableEffect(firstItemRequester) {
        controllerNav?.contentFocusRequester = firstItemRequester
        onDispose {
            if (controllerNav?.contentFocusRequester == firstItemRequester) {
                controllerNav?.contentFocusRequester = null
            }
        }
    }

    LaunchedEffect(Unit) {
        yield()
        runCatching { firstItemRequester.requestFocus() }
    }

    val controllers = gameMenuRequest.coreConfig.controllerConfigs
    val visibleControllers =
        (0 until maxOf(1, connectedGamePads))
            .map { it to controllers[it] }
            .filter { (_, c) -> c != null && c.size >= 2 }

    val hasControllers = visibleControllers.isNotEmpty()

    OmnidroidSettingsPage {
        ControllersOptions(
            gameMenuRequest,
            maxOf(1, connectedGamePads),
            context,
            firstItemFocusRequester = if (hasControllers) firstItemRequester else null,
        )
        if (allOptions.isNotEmpty()) {
            OmnidroidCardSettingsGroup {
                CoreOptions(
                    gameMenuRequest.game.systemId,
                    allOptions,
                    context,
                    firstItemFocusRequester = if (!hasControllers) firstItemRequester else null,
                )
            }
        }
    }
}

@Composable
private fun CoreOptions(
    systemID: String,
    coreOptions: List<OmnidroidCoreOption>,
    context: Context,
    firstItemFocusRequester: FocusRequester? = null,
) {
    var hasAppliedFirstFocus = false
    for (coreOption in coreOptions) {
        val entryValues = coreOption.getEntriesValues()
        val entries = coreOption.getEntries(context)
        if (entryValues.isEmpty() || entries.isEmpty()) {
            continue
        }

        val itemModifier =
            if (!hasAppliedFirstFocus && firstItemFocusRequester != null) {
                hasAppliedFirstFocus = true
                Modifier.focusRequester(firstItemFocusRequester)
            } else {
                Modifier
            }

        if (entryValues.toSet() == CoreOptionsPreferenceHelper.BOOLEAN_SET) {
            OmnidroidSettingsSwitch(
                modifier = itemModifier,
                state =
                    booleanPreferenceState(
                        CoreVariablesManager.computeSharedPreferenceKey(coreOption.getKey(), systemID),
                        coreOption.getCurrentValue() == "enabled",
                    ),
                title = { Text(text = coreOption.getDisplayName(context)) },
            )
        } else {
            OmnidroidSettingsList(
                modifier = itemModifier,
                title = { Text(text = coreOption.getDisplayName(context)) },
                items = entries,
                state =
                    indexPreferenceState(
                        CoreVariablesManager.computeSharedPreferenceKey(coreOption.getKey(), systemID),
                        entryValues.first(),
                        entryValues,
                    ),
            )
        }
    }
}

@Composable
private fun ControllersOptions(
    gameMenuRequest: GameMenuActivity.GameMenuRequest,
    connectedGamePads: Int,
    context: Context,
    firstItemFocusRequester: FocusRequester? = null,
) {
    val controllers = gameMenuRequest.coreConfig.controllerConfigs

    val visibleControllers =
        (0 until connectedGamePads)
            .map { it to controllers[it] }
            .filter { (_, controllers) -> controllers != null && controllers.size >= 2 }

    if (visibleControllers.isEmpty()) {
        return
    }

    OmnidroidCardSettingsGroup(
        title = { Text(text = stringResource(R.string.core_settings_category_controllers)) },
    ) {
        visibleControllers.forEachIndexed { index, (port, controllerConfigs) ->
            OmnidroidSettingsList(
                modifier = if (index == 0 && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                title = { Text(text = context.getString(R.string.core_settings_controller, (port + 1).toString())) },
                items = controllerConfigs!!.map { stringResource(id = it.displayName) },
                state =
                    indexPreferenceState(
                        ControllerConfigsManager.getSharedPreferencesId(
                            gameMenuRequest.game.systemId,
                            gameMenuRequest.coreConfig.coreID,
                            port,
                        ),
                        controllerConfigs.map { it.name }.first(),
                        controllerConfigs.map { it.name },
                    ),
            )
        }
    }
}
