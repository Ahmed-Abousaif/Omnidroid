package com.omnidroid.app.mobile.feature.gamemenu

import com.omnidroid.app.mobile.shared.compose.sidemenu.SideMenuDestination
import com.omnidroid.app.mobile.shared.compose.sidemenu.validateSideMenuDestinations
import com.omnidroid.app.mobile.shared.compose.sidemenu.SideMenuNavHost
import com.omnidroid.app.mobile.shared.compose.sidemenu.SideMenuScaffold
import com.omnidroid.app.mobile.shared.compose.sidemenu.rememberSideMenuState
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.gamemenu.coreoptions.GameMenuCoreOptionsScreen
import com.omnidroid.app.mobile.feature.gamemenu.coreoptions.GameMenuCoreOptionsViewModel
import com.omnidroid.app.mobile.feature.gamemenu.states.GameMenuStatesScreen
import com.omnidroid.app.mobile.feature.gamemenu.states.GameMenuStatesViewModel
import com.omnidroid.app.mobile.shared.compose.ui.AppTheme
import com.omnidroid.app.mobile.shared.controller.ControllerHintBar
import com.omnidroid.app.mobile.shared.controller.ControllerHintBarRowHeight
import com.omnidroid.app.mobile.shared.controller.ControllerHints
import com.omnidroid.app.mobile.shared.controller.ControllerInputBridge
import com.omnidroid.app.mobile.shared.controller.ControllerNavigationState
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.app.shared.GameMenuContract
import com.omnidroid.app.shared.coreoptions.OmnidroidCoreOption
import com.omnidroid.app.shared.input.InputDeviceManager
import com.omnidroid.app.shared.input.omnidroiddevice.OmnidroidInputDeviceGamePad
import com.omnidroid.app.shared.input.omnidroiddevice.getOmnidroidInputDevice
import com.omnidroid.common.kotlin.serializable
import com.omnidroid.lib.android.RetrogradeComponentActivity
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.saves.StatesManager
import com.omnidroid.lib.saves.StatesPreviewManager
import com.omnidroid.lib.savesync.SaveSyncManager
import com.omnidroid.touchinput.radial.sensors.TiltConfiguration
import java.security.InvalidParameterException
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GameMenuActivity : RetrogradeComponentActivity() {
    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var statesManager: StatesManager

    @Inject
    lateinit var statesPreviewManager: StatesPreviewManager

    @Inject
    lateinit var saveSyncManager: SaveSyncManager

    private val controllerBridge = ControllerInputBridge()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        hideSystemBars()

        val extras = intent.extras
        val legacyFastForwardEnabled = extras?.getBoolean(GameMenuContract.EXTRA_FAST_FORWARD, false) ?: false
        val frameSpeed =
            extras?.getInt(
                GameMenuContract.EXTRA_FRAME_SPEED,
                if (legacyFastForwardEnabled) 2 else 1,
            ) ?: 1

        val gameMenuRequest =
            GameMenuRequest(
                coreOptions =
                    intent.serializable<Array<OmnidroidCoreOption>>(GameMenuContract.EXTRA_CORE_OPTIONS)
                        ?.toList()
                        ?: throw InvalidParameterException("Missing EXTRA_CORE_OPTIONS"),
                advancedCoreOptions =
                    intent.serializable<Array<OmnidroidCoreOption>>(GameMenuContract.EXTRA_ADVANCED_CORE_OPTIONS)
                        ?.toList()
                        ?: throw InvalidParameterException("Missing EXTRA_ADVANCED_CORE_OPTIONS"),
                game =
                    intent.serializable<Game>(GameMenuContract.EXTRA_GAME)
                        ?: throw InvalidParameterException("Missing EXTRA_GAME"),
                coreConfig =
                    intent.serializable<SystemCoreConfig>(GameMenuContract.EXTRA_SYSTEM_CORE_CONFIG)
                        ?: throw InvalidParameterException("Missing EXTRA_SYSTEM_CORE_CONFIG"),
                audioEnabled =
                    extras?.getBoolean(GameMenuContract.EXTRA_AUDIO_ENABLED, false) ?: false,
                fastForwardSupported =
                    extras?.getBoolean(GameMenuContract.EXTRA_FAST_FORWARD_SUPPORTED, false) ?: false,
                frameSpeed = frameSpeed,
                numDisks =
                    extras?.getInt(GameMenuContract.EXTRA_DISKS, 0) ?: 0,
                currentDisk =
                    extras?.getInt(GameMenuContract.EXTRA_CURRENT_DISK, 0) ?: 0,
                currentTiltConfiguration =
                    intent.serializable<TiltConfiguration>(GameMenuContract.EXTRA_CURRENT_TILT_CONFIG)
                        ?: TiltConfiguration.Disabled,
                allTiltConfigurations =
                    intent.serializable<Array<TiltConfiguration>>(GameMenuContract.EXTRA_TILT_ALL_CONFIGS)
                        ?.toList()
                        ?: emptyList(),
                editTouchControlsSupported =
                    extras?.getBoolean(GameMenuContract.EXTRA_EDIT_TOUCH_CONTROLS_SUPPORTED, true) ?: true,
            )

        setContent {
            GameMenuScreen(gameMenuRequest)
        }
    }

    @Composable
    private fun GameMenuScreen(gameMenuRequest: GameMenuRequest) {
        AppTheme(syncSystemBars = false) {
            remember {
                validateSideMenuDestinations(GameMenuRoute.entries.map { SideMenuDestination(it.route, it.titleId, it.parent?.route) })
            }
            val navController = rememberNavController()
            val controllerNav = remember { ControllerNavigationState() }
            val menu = rememberSideMenuState()
            var pendingCommand by remember { mutableStateOf<InGameMenuCommand?>(null) }
            val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                ?.let { GameMenuRoute.findByRoute(it) } ?: GameMenuRoute.HOME
            val gamePads = remember { inputDeviceManager.getGamePadsObservable() }
            val gamepadConnected = gamePads.collectAsState(emptyList()).value
                .any { it.getOmnidroidInputDevice() is OmnidroidInputDeviceGamePad }
            val hints = ControllerHints.standard(
                select = stringResource(R.string.controller_hint_select),
                back = stringResource(R.string.controller_hint_back),
            )
            fun requestClose(command: InGameMenuCommand) {
                if (!menu.transition.targetState) return
                pendingCommand = command
                menu.closeThen()
            }
            SideEffect {
                controllerBridge.navigation = controllerNav
                controllerNav.onBack = {
                    if (currentRoute.canGoBack()) navController.popBackStack() else menu.closeThen()
                }
                controllerNav.setHints(hints)
            }
            CompositionLocalProvider(LocalControllerNavigation provides controllerNav) {
                Box(Modifier.fillMaxSize()) {
                    SideMenuScaffold(
                        state = menu,
                        title = stringResource(currentRoute.titleId),
                        canGoBack = currentRoute.canGoBack(),
                        onBack = { navController.popBackStack() },
                        bottomReserved = if (gamepadConnected) ControllerHintBarRowHeight else 0.dp,
                        onClosed = { onResult { pendingCommand?.writeTo(this) } },
                    ) {
                        SideMenuNavHost(navController, GameMenuRoute.HOME.route) {
                            composable(GameMenuRoute.HOME) {
                                GameMenuHomeScreen(
                                    navController,
                                    gameMenuRequest,
                                    onCommand = { requestClose(it) },
                                    saveSyncSupported = saveSyncManager.isSupported(),
                                )
                            }
                            composable(GameMenuRoute.SAVE) {
                                GameMenuStatesScreen(
                                    viewModel(
                                        factory =
                                            GameMenuStatesViewModel.Factory(
                                                application,
                                                gameMenuRequest,
                                                statesManager,
                                                false,
                                                statesPreviewManager,
                                            ),
                                    ),
                                    onStateClicked = {
                                        requestClose(InGameMenuCommand.Save(it))
                                    },
                                    onNewStateClicked = { requestClose(InGameMenuCommand.SaveNew) },
                                )
                            }
                            composable(GameMenuRoute.LOAD) {
                                GameMenuStatesScreen(
                                    viewModel(
                                        factory =
                                            GameMenuStatesViewModel.Factory(
                                                application,
                                                gameMenuRequest,
                                                statesManager,
                                                true,
                                                statesPreviewManager,
                                            ),
                                    ),
                                    onStateClicked = {
                                        requestClose(InGameMenuCommand.Load(it))
                                    },
                                    onAutoSaveClicked = { requestClose(InGameMenuCommand.LoadAutoSave) },
                                )
                            }
                            composable(GameMenuRoute.OPTIONS) {
                                GameMenuCoreOptionsScreen(
                                    viewModel(
                                        factory =
                                            GameMenuCoreOptionsViewModel.Factory(
                                                inputDeviceManager,
                                            ),
                                    ),
                                    gameMenuRequest,
                                )
                            }
                        }
                    }
                    if (gamepadConnected) {
                        ControllerHintBar(
                            visible = menu.transition.targetState,
                            hints = controllerNav.hints.value,
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    private fun onResult(block: Intent.() -> Unit) {
        val resultIntent = Intent()
        resultIntent.block()
        setResult(RESULT_OK, resultIntent)
        finish()
        overridePendingTransition(0, 0)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && isNavigationKey(event.keyCode)) {
            runCatching { controllerBridge.navigation?.contentFocusRequester?.requestFocus() }
        }
        return controllerBridge.dispatchKey(event) { super.dispatchKeyEvent(it) }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        runCatching { controllerBridge.navigation?.contentFocusRequester?.requestFocus() }
        if (controllerBridge.dispatchMotion(event) { super.dispatchKeyEvent(it) }) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun isNavigationKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_BUTTON_A ||
            keyCode == KeyEvent.KEYCODE_BUTTON_1 ||
            keyCode == KeyEvent.KEYCODE_ENTER
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
