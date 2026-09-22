@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.omnidroid.app.mobile.feature.gamemenu

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.gamemenu.coreoptions.GameMenuCoreOptionsScreen
import com.omnidroid.app.mobile.feature.gamemenu.coreoptions.GameMenuCoreOptionsViewModel
import com.omnidroid.app.mobile.feature.gamemenu.states.GameMenuStatesScreen
import com.omnidroid.app.mobile.feature.gamemenu.states.GameMenuStatesViewModel
import com.omnidroid.app.mobile.shared.compose.ui.AppTheme
import com.omnidroid.app.mobile.shared.compose.ui.HomeChromeBackground
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.controller.ControllerHintBar
import com.omnidroid.app.mobile.shared.controller.ControllerHintBarRowHeight
import com.omnidroid.app.mobile.shared.controller.ControllerHints
import com.omnidroid.app.mobile.shared.controller.ControllerInputBridge
import com.omnidroid.app.mobile.shared.controller.ControllerNavigationState
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow
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
import kotlinx.coroutines.yield
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

    data class GameMenuRequest(
        val coreOptions: List<OmnidroidCoreOption>,
        val advancedCoreOptions: List<OmnidroidCoreOption>,
        val game: Game,
        val coreConfig: SystemCoreConfig,
        val audioEnabled: Boolean,
        val fastForwardSupported: Boolean,
        val frameSpeed: Int,
        val numDisks: Int,
        val currentDisk: Int,
        val currentTiltConfiguration: TiltConfiguration,
        val allTiltConfigurations: List<TiltConfiguration>,
    )

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
            )

        setContent {
            GameMenuScreen(gameMenuRequest)
        }
    }

    @Composable
    private fun GameMenuScreen(gameMenuRequest: GameMenuRequest) {
        AppTheme {
            val navController = rememberNavController()
            val controllerNav = remember { ControllerNavigationState() }
            val panelState =
                remember {
                    MutableTransitionState(false).apply { targetState = true }
                }
            var pendingResult by remember { mutableStateOf<Intent.() -> Unit>({}) }
            var hasOpened by remember { mutableStateOf(false) }

            val currentRoute =
                navController.currentBackStackEntryAsState().value?.destination?.route
                    ?.let { GameMenuRoute.findByRoute(it) }
                    ?: GameMenuRoute.HOME

            val gamepadConnected =
                inputDeviceManager.getGamePadsObservable()
                    .collectAsState(emptyList())
                    .value
                    .any { it.getOmnidroidInputDevice() is OmnidroidInputDeviceGamePad }

            val hints =
                ControllerHints.standard(
                    select = stringResource(R.string.controller_hint_select),
                    back = stringResource(R.string.controller_hint_back),
                )

            fun requestClose(block: Intent.() -> Unit = {}) {
                if (!panelState.targetState) return
                pendingResult = block
                panelState.targetState = false
            }

            LaunchedEffect(panelState.currentState, panelState.targetState, panelState.isIdle) {
                if (panelState.currentState) hasOpened = true
                if (hasOpened && panelState.isIdle && !panelState.currentState && !panelState.targetState) {
                    onResult(pendingResult)
                }
                if (panelState.targetState && panelState.isIdle) {
                    yield()
                    runCatching { controllerNav.contentFocusRequester?.requestFocus() }
                }
            }

            LaunchedEffect(currentRoute) {
                controllerNav.setHints(hints)
                yield()
                runCatching { controllerNav.contentFocusRequester?.requestFocus() }
            }

            controllerNav.onBack = {
                if (currentRoute.canGoBack()) {
                    navController.popBackStack()
                } else {
                    requestClose()
                }
            }

            SideEffect {
                controllerBridge.navigation = controllerNav
            }

            BackHandler {
                if (currentRoute.canGoBack()) {
                    navController.popBackStack()
                } else {
                    requestClose()
                }
            }

            val scrimAlpha by animateFloatAsState(
                targetValue = if (panelState.targetState) 0.58f else 0f,
                animationSpec = tween(durationMillis = if (panelState.targetState) 280 else 220),
                label = "gameMenuScrim",
            )

            CompositionLocalProvider(
                LocalControllerNavigation provides controllerNav,
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val panelWidth =
                        remember(maxWidth) {
                            maxOf(320.dp, minOf(maxWidth * 0.42f, 440.dp))
                        }

                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = scrimAlpha))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { requestClose() },
                    )

                    AnimatedVisibility(
                        visibleState = panelState,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        enter =
                            slideInHorizontally(
                                animationSpec = tween(280, easing = FastOutSlowInEasing),
                            ) { it },
                        exit =
                            slideOutHorizontally(
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                            ) { it },
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxHeight().width(panelWidth),
                            color = HomeChromeBackground,
                            contentColor = Color.White,
                            shape = RectangleShape,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(3.dp)
                                            .fillMaxHeight()
                                            .background(LibraryNeonGreen),
                                )
                                Column(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .padding(
                                                bottom =
                                                    if (gamepadConnected) ControllerHintBarRowHeight else 0.dp,
                                            ),
                                ) {
                                    GameMenuHeader(
                                        title = stringResource(currentRoute.titleId),
                                        canGoBack = currentRoute.canGoBack(),
                                        onBack = { navController.popBackStack() },
                                        onClose = { requestClose() },
                                    )
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(Color.White.copy(alpha = 0.08f)),
                                    )
                                    NavHost(
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .focusGroup(),
                                        navController = navController,
                                        startDestination = GameMenuRoute.HOME.route,
                                        enterTransition = { fadeIn(animationSpec = tween(160)) },
                                        exitTransition = { fadeOut(animationSpec = tween(160)) },
                                    ) {
                                        composable(GameMenuRoute.HOME) {
                                            GameMenuHomeScreen(
                                                navController,
                                                gameMenuRequest,
                                                onResult = { requestClose(it) },
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
                                                    requestClose { putExtra(GameMenuContract.RESULT_SAVE, it) }
                                                },
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
                                                    requestClose { putExtra(GameMenuContract.RESULT_LOAD, it) }
                                                },
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
                            }
                        }
                    }

                    if (gamepadConnected) {
                        ControllerHintBar(
                            visible = panelState.targetState,
                            hints = controllerNav.hints.value,
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun GameMenuHeader(
        title: String,
        canGoBack: Boolean,
        onBack: () -> Unit,
        onClose: () -> Unit,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = { if (canGoBack) onBack() else onClose() },
                modifier =
                    Modifier
                        .size(40.dp)
                        .controllerFocusGlow(CircleShape),
                shape = CircleShape,
                color = Color.Transparent,
                contentColor = Color.White,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (canGoBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    } else {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                }
            }
            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp),
                maxLines = 1,
            )
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
