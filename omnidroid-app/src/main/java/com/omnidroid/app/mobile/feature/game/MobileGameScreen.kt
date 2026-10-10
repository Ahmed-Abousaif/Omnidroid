package com.omnidroid.app.mobile.feature.game

import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt
import com.omnidroid.app.shared.game.BaseGameScreenViewModel
import com.omnidroid.app.shared.game.viewmodel.GameViewModelTouchControls.Companion.MENU_LOADING_ANIMATION_MILLIS
import com.omnidroid.lib.controller.ControllerConfig
import com.omnidroid.touchinput.R
import com.omnidroid.touchinput.radial.OmnidroidPadTheme
import com.omnidroid.touchinput.radial.LocalOmnidroidPadTheme
import com.omnidroid.touchinput.radial.sensors.TiltConfiguration
import com.omnidroid.touchinput.radial.settings.TouchControllerSettingsManager
import com.omnidroid.touchinput.radial.ui.GlassSurface
import com.omnidroid.app.mobile.shared.compose.ui.rememberDisplayCutoutInsetsForEdge
import com.omnidroid.app.mobile.shared.compose.ui.rememberDisplayCutoutInsetsForTopBar
import com.omnidroid.app.shared.game.view.IRetroGameView
import com.omnidroid.touchinput.radial.ui.OmnidroidButtonPressFeedback
import gg.padkit.PadKit
import gg.padkit.config.HapticFeedbackType
import gg.padkit.inputstate.InputState

@Composable
fun MobileGameScreen(viewModel: BaseGameScreenViewModel) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = constraints.maxWidth > constraints.maxHeight

        LaunchedEffect(isLandscape) {
            val orientation =
                if (isLandscape) {
                    TouchControllerSettingsManager.Orientation.LANDSCAPE
                } else {
                    TouchControllerSettingsManager.Orientation.PORTRAIT
                }
            viewModel.onScreenOrientationChanged(orientation)
        }

        val controllerConfigState = viewModel.getTouchControllerConfig().collectAsState(null)
        val touchControlsVisibleState = viewModel.isTouchControllerVisible().collectAsState(false)
        val forceHidePadsState = viewModel.isForceHideTouchControls().collectAsState(false)
        val touchControllerSettingsState =
            viewModel
                .getTouchControlsSettings()
                .collectAsState(null)

        val touchControllerSettings = touchControllerSettingsState.value
        val currentControllerConfig = controllerConfigState.value

        val tiltConfiguration = viewModel.getTiltConfiguration().collectAsState(TiltConfiguration.Disabled)
        val tiltSimulatedStates = viewModel.getSimulatedTiltEvents().collectAsState(InputState())
        val tiltSimulatedControls = remember { derivedStateOf { tiltConfiguration.value.controlIds() } }

        val touchGamePads = currentControllerConfig?.getTouchControllerConfig()
        val leftGamePad = touchGamePads?.leftComposable
        val rightGamePad = touchGamePads?.rightComposable

        val localContext = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current
        val density = LocalDensity.current

        val fullScreenPosition = remember { mutableStateOf<Rect?>(null) }
        val viewportPosition = remember { mutableStateOf<Rect?>(null) }
        val retroViewState = remember { mutableStateOf<IRetroGameView?>(null) }

        val fullPos = fullScreenPosition.value
        val viewPos = viewportPosition.value

        // Pads are laid out edge-to-edge, and the user can raise `scale` or drop the
        // margins, so inset them out of the display cutout here rather than relying on a
        // default margin. Otherwise a camera hole can end up underneath a button.
        val padCutoutInsets = rememberDisplayCutoutInsetsForEdge(isLandscape)

        val isVisible =
            touchControllerSettings != null &&
                currentControllerConfig != null &&
                touchControlsVisibleState.value

        val gameAspectRatioState =
            viewModel.getGameAspectRatio().collectAsState(viewModel.system.aspectRatio)

        LaunchedEffect(fullPos, viewPos) {
            val gameView = viewModel.retroGameView.retroGameViewFlow()
            if (fullPos == null || viewPos == null) return@LaunchedEffect
            val viewport =
                RectF(
                    (viewPos.left - fullPos.left) / fullPos.width,
                    (viewPos.top - fullPos.top) / fullPos.height,
                    (viewPos.right - fullPos.left) / fullPos.width,
                    (viewPos.bottom - fullPos.top) / fullPos.height,
                )
            gameView.viewport = viewport
        }

        // PadKit has no intensity API; touch haptics are played in GameViewModelTouchControls.
        // Touchscreen overlay is a sibling (not nested) so its pointerInput cannot break
        // PadKit multi-touch for virtual buttons.
        PadKit(
            modifier = Modifier.fillMaxSize(),
            onInputEvents = { viewModel.handleVirtualInputEvent(it) },
            hapticFeedbackType = HapticFeedbackType.NONE,
            simulatedState = tiltSimulatedStates,
            simulatedControlIds = tiltSimulatedControls,
        ) {
            AndroidView(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { fullScreenPosition.value = it.boundsInRoot() },
                factory = {
                    viewModel.createRetroView(localContext, lifecycle).let { gameView ->
                        retroViewState.value = gameView
                        gameView.asView()
                    }
                },
            )

            ConstraintLayout(
                modifier = Modifier.fillMaxSize(),
                constraintSet =
                    GameScreenLayout.buildConstraintSet(
                        isLandscape = isLandscape,
                        allowTouchOverlay = currentControllerConfig?.allowTouchOverlay ?: true,
                        padsVisible = isVisible,
                    ),
            ) {
                val screenPosY =
                    touchControllerSettings?.screenPositionY
                        ?: TouchControllerSettingsManager.DEFAULT_SCREEN_POSITION_Y
                val verticalBias = (screenPosY * 2f - 1f).coerceIn(-1f, 1f)
                val targetAspect = gameAspectRatioState.value

                BoxWithConstraints(
                    modifier =
                        Modifier
                            .layoutId(GameScreenLayout.CONSTRAINTS_GAME_VIEW)
                            .windowInsetsPadding(
                                rememberDisplayCutoutInsetsForTopBar(isLandscape),
                            )
                            .fillMaxSize(),
                ) {
                    val containerAspect =
                        if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else targetAspect
                    val boxWidth =
                        if (targetAspect > containerAspect) maxWidth else maxHeight * targetAspect
                    val boxHeight =
                        if (targetAspect > containerAspect) maxWidth / targetAspect else maxHeight

                    Box(
                        modifier =
                            Modifier
                                .size(width = boxWidth, height = boxHeight)
                                .align(BiasAlignment(horizontalBias = 0f, verticalBias = verticalBias))
                                .onGloballyPositioned { viewportPosition.value = it.boundsInRoot() },
                    )
                }

                if (isVisible) {
                    CompositionLocalProvider(LocalOmnidroidPadTheme provides OmnidroidPadTheme()) {
                        if (!isLandscape) {
                            PadContainer(
                                modifier =
                                    Modifier
                                        .layoutId(GameScreenLayout.CONSTRAINTS_BOTTOM_CONTAINER)
                                        .windowInsetsPadding(padCutoutInsets),
                            )
                        } else if (!currentControllerConfig.allowTouchOverlay) {
                            PadContainer(
                                modifier =
                                    Modifier
                                        .layoutId(GameScreenLayout.CONSTRAINTS_LEFT_CONTAINER)
                                        .windowInsetsPadding(padCutoutInsets),
                            )
                            PadContainer(
                                modifier =
                                    Modifier
                                        .layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_CONTAINER)
                                        .windowInsetsPadding(padCutoutInsets),
                            )
                        }

                        leftGamePad?.invoke(
                            this,
                            Modifier
                                .layoutId(GameScreenLayout.CONSTRAINTS_LEFT_PAD)
                                .windowInsetsPadding(padCutoutInsets),
                            touchControllerSettings,
                        )
                        rightGamePad?.invoke(
                            this,
                            Modifier
                                .layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_PAD)
                                .windowInsetsPadding(padCutoutInsets),
                            touchControllerSettings,
                        )

                        GameScreenRunningCentralMenu(
                            modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_GAME_CONTAINER),
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }

        val showEditControlsState = viewModel.isEditControlShown().collectAsState(false)
        if (showEditControlsState.value && touchControllerSettings != null) {
            MenuEditTouchControls(
                viewModel = viewModel,
                controllerConfig = currentControllerConfig,
                touchControllerSettings = touchControllerSettings,
                isTouchControlsVisible = isVisible,
            )
        }

        // Viewport-sized sibling above the game image only (NDS/3DS pads sit beside/below it).
        // Kept outside PadKit so its pointerInput cannot collapse PadKit multi-touch.
        if (viewModel.hasTouchScreen && viewPos != null) {
            TouchScreenPointerOverlay(
                enabled = true,
                retroView = retroViewState.value,
                retroViewBoundsInRoot = fullPos,
                touchScreenBoundsInRoot = viewPos,
                modifier =
                    Modifier
                        .offset {
                            IntOffset(viewPos.left.roundToInt(), viewPos.top.roundToInt())
                        }
                        .size(
                            width = with(density) { viewPos.width.toDp() },
                            height = with(density) { viewPos.height.toDp() },
                        ),
            )
        }

        AnimatedVisibility(
            visible = viewModel.hasTouchScreen && forceHidePadsState.value,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            TouchControlsRestoreTab(
                onClick = { viewModel.showTouchControls() },
            )
        }

        val isLoading =
            viewModel.loadingState
                .collectAsState(true)
                .value

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun TouchControlsRestoreTab(onClick: () -> Unit) {
    Surface(
        modifier =
            Modifier
                .padding(bottom = 8.dp)
                .width(72.dp)
                .height(28.dp)
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PadContainer(modifier: Modifier = Modifier) {
    val theme = LocalOmnidroidPadTheme.current
    GlassSurface(
        modifier = modifier,
        cornerRadius = theme.level0CornerRadius,
        fillColor = theme.level0Fill,
        shadowColor = theme.level0Shadow,
        shadowWidth = theme.level0ShadowWidth,
    )
}

@Composable
private fun GameScreenRunningCentralMenu(
    modifier: Modifier = Modifier,
    viewModel: BaseGameScreenViewModel,
) {
    val menuPressed = viewModel.isMenuPressed().collectAsState(false)
    Box(
        modifier = modifier.wrapContentSize(),
        contentAlignment = Alignment.Center,
    ) {
        OmnidroidButtonPressFeedback(
            pressed = menuPressed.value,
            animationDurationMillis = MENU_LOADING_ANIMATION_MILLIS,
            icon = R.drawable.button_menu,
        )
    }
}

@Composable
private fun MenuEditTouchControls(
    viewModel: BaseGameScreenViewModel,
    controllerConfig: ControllerConfig?,
    touchControllerSettings: TouchControllerSettingsManager.Settings,
    isTouchControlsVisible: Boolean,
) {
    Dialog(onDismissRequest = { viewModel.showEditControls(false) }) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MenuEditTouchControlSlider(
                    icon = Icons.Default.Height,
                    label = stringResource(R.string.touch_customize_screen_position),
                    rotation = 0f,
                ) {
                    Slider(
                        value = touchControllerSettings.screenPositionY,
                        onValueChange = {
                            viewModel.updateTouchControllerSettings(
                                touchControllerSettings.copy(screenPositionY = it),
                            )
                        },
                    )
                }

                if (isTouchControlsVisible && controllerConfig != null) {
                    MenuEditTouchControlSlider(
                        icon = Icons.Default.OpenInFull,
                        label = stringResource(R.string.touch_customize_scale),
                        rotation = 0f,
                    ) {
                        Slider(
                            value = touchControllerSettings.scale,
                            onValueChange = {
                                viewModel.updateTouchControllerSettings(
                                    touchControllerSettings.copy(scale = it),
                                )
                            },
                        )
                    }

                    MenuEditTouchControlSlider(
                        icon = Icons.Default.Height,
                        label = stringResource(R.string.touch_customize_margin_x),
                        rotation = 90f,
                    ) {
                        Slider(
                            value = touchControllerSettings.marginX,
                            onValueChange = {
                                viewModel.updateTouchControllerSettings(
                                    touchControllerSettings.copy(marginX = it),
                                )
                            },
                        )
                    }

                    MenuEditTouchControlSlider(
                        icon = Icons.Default.Height,
                        label = stringResource(R.string.touch_customize_margin_y),
                        rotation = 0f,
                    ) {
                        Slider(
                            value = touchControllerSettings.marginY,
                            onValueChange = {
                                viewModel.updateTouchControllerSettings(
                                    touchControllerSettings.copy(marginY = it),
                                )
                            },
                        )
                    }

                    if (controllerConfig.allowTouchRotation) {
                        MenuEditTouchControlSlider(
                            icon = Icons.AutoMirrored.Filled.RotateLeft,
                            label = stringResource(R.string.touch_customize_rotation),
                            rotation = 0f,
                        ) {
                            Slider(
                                value = touchControllerSettings.rotation,
                                onValueChange = {
                                    viewModel.updateTouchControllerSettings(
                                        touchControllerSettings.copy(rotation = it),
                                    )
                                },
                            )
                        }
                    }

                    if (controllerConfig.allowDpadDiagonalsToggle) {
                        val dpadLabel = stringResource(R.string.touch_customize_dpad_8way)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                modifier = Modifier.size(18.dp),
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = dpadLabel,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = dpadLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = touchControllerSettings.allowDiagonals,
                                onCheckedChange = {
                                    viewModel.updateTouchControllerSettings(
                                        touchControllerSettings.copy(allowDiagonals = it),
                                    )
                                },
                            )
                        }
                    }

                    if (controllerConfig.allowDpadAsAnalog) {
                        val dpadAnalogLabel = stringResource(R.string.touch_customize_dpad_as_analog)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                modifier = Modifier.size(18.dp),
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = dpadAnalogLabel,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = dpadAnalogLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = touchControllerSettings.dpadStyle == TouchControllerSettingsManager.DpadStyle.ANALOG,
                                onCheckedChange = { isAnalog ->
                                    viewModel.updateTouchControllerSettings(
                                        touchControllerSettings.copy(
                                            dpadStyle =
                                                if (isAnalog) {
                                                    TouchControllerSettingsManager.DpadStyle.ANALOG
                                                } else {
                                                    TouchControllerSettingsManager.DpadStyle.CROSS
                                                },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { viewModel.resetTouchControls() },
                        modifier = Modifier.weight(1f),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                            ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.touch_customize_button_reset),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Button(
                        onClick = { viewModel.showEditControls(false) },
                        modifier = Modifier.weight(1f),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E7D32),
                                contentColor = Color.White,
                            ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.touch_customize_button_save),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuEditTouchControlSlider(
    icon: ImageVector,
    label: String,
    rotation: Float = 0f,
    slider: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                modifier =
                    Modifier
                        .size(18.dp)
                        .rotate(rotation),
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        slider()
    }
}
