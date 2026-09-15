package com.omnidroid.app.mobile.feature.addconsole

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.addconsole.AddConsoleViewModel.ConsoleItem
import com.omnidroid.app.mobile.shared.compose.ui.HomeChromeBackground
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val Squircle = RoundedCornerShape(28.dp)
private val ButtonShape = RoundedCornerShape(16.dp)
private val HeroSize = 156.dp
private val ItemSpacing = 212.dp
private val BackgroundScale = 0.76f
private val BackgroundAlpha = 0.42f
private val CarouselLift = 28.dp

@Composable
fun AddConsoleScreen(
    modifier: Modifier = Modifier,
    viewModel: AddConsoleViewModel,
) {
    val state = viewModel.state.collectAsState().value
    val snackbarHostState = remember { SnackbarHostState() }
    val consoles = state.consoles
    val lastIndex = (consoles.size - 1).coerceAtLeast(0)
    var selectedIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val navigation = LocalControllerNavigation.current
    val scope = rememberCoroutineScope()
    val position = remember { Animatable(0f) }
    val spacingPx = with(LocalDensity.current) { ItemSpacing.toPx() }
    val animatedIndex = position.value

    fun animateToIndex(index: Int) {
        if (consoles.isEmpty()) return
        val target = index.coerceIn(0, lastIndex)
        selectedIndex = target
        scope.launch {
            position.animateTo(
                target.toFloat(),
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 280f),
            )
        }
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeError()
    }

    LaunchedEffect(consoles.size) {
        if (selectedIndex > lastIndex) {
            selectedIndex = lastIndex
            position.snapTo(lastIndex.toFloat())
        }
        runCatching { focusRequester.requestFocus() }
    }

    fun moveBy(delta: Int) {
        animateToIndex(selectedIndex + delta)
    }

    fun confirmSelection() {
        val console = consoles.getOrNull(selectedIndex) ?: return
        if (!console.installed && state.installing == null) {
            viewModel.install(console.metaSystemID)
        }
    }

    navigation?.onCarouselPrev = { moveBy(-1) }
    navigation?.onCarouselNext = { moveBy(1) }
    navigation?.onCarouselConfirm = { confirmSelection() }

    val selected = consoles.getOrNull(selectedIndex)
    val accent = selected?.let { Color(it.metaSystemID.color()) } ?: Color(0xFF3EE366)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(HomeChromeBackground)
                .centerGlow(accent)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            moveBy(-1)
                            true
                        }
                        Key.DirectionRight -> {
                            moveBy(1)
                            true
                        }
                        Key.DirectionCenter,
                        Key.Enter,
                        -> {
                            confirmSelection()
                            true
                        }
                        else -> false
                    }
                }
                .pointerInput(consoles.size, spacingPx, lastIndex) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            scope.launch { position.stop() }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val next =
                                (position.value - dragAmount / spacingPx)
                                    .coerceIn(0f, lastIndex.toFloat())
                            scope.launch { position.snapTo(next) }
                            val rounded = next.roundToInt()
                            if (rounded != selectedIndex) {
                                selectedIndex = rounded
                            }
                        },
                        onDragEnd = {
                            val projected =
                                position.value - tracker.calculateVelocity().x / spacingPx * 0.22f
                            animateToIndex(projected.roundToInt())
                        },
                        onDragCancel = {
                            animateToIndex(position.value.roundToInt())
                        },
                    )
                },
    ) {
        if (consoles.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            return@Box
        }

        Box(modifier = Modifier.fillMaxSize().offset(y = -CarouselLift)) {
            ConsoleLine(
                consoles = consoles,
                animatedIndex = animatedIndex,
                selectedIndex = selectedIndex,
                installing = state.installing,
                onSelect = { animateToIndex(it) },
                onConfirm = { confirmSelection() },
            )
            selected?.let { console ->
                val installing = state.installing == console.metaSystemID
                Text(
                    text = consoleStatus(console, installing),
                    color = accent,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .offset(y = -(HeroSize / 2 + 22.dp))
                            .padding(horizontal = 24.dp),
                )
                ConsoleDetails(
                    console = console,
                    installing = installing,
                    accent = accent,
                    enabled = state.installing == null && !console.installed,
                    onInstall = { viewModel.install(console.metaSystemID) },
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .offset(y = HeroSize / 2 + 48.dp),
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 76.dp),
        )
    }
}

@Composable
private fun ConsoleLine(
    consoles: List<ConsoleItem>,
    animatedIndex: Float,
    selectedIndex: Int,
    installing: com.omnidroid.lib.library.MetaSystemID?,
    onSelect: (Int) -> Unit,
    onConfirm: () -> Unit,
) {
    val spacingPx = with(LocalDensity.current) { ItemSpacing.toPx() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        consoles.forEachIndexed { index, console ->
            val distance = (index - animatedIndex).absoluteValue
            if (distance > 3.2f) return@forEachIndexed
            val front = (1f - distance).coerceIn(0f, 1f)
            val scale = BackgroundScale + (1f - BackgroundScale) * front
            val alpha = BackgroundAlpha + (1f - BackgroundAlpha) * front
            val selected = index == selectedIndex

            Box(
                modifier =
                    Modifier
                        .zIndex(front)
                        .graphicsLayer {
                            translationX = (index - animatedIndex) * spacingPx
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .size(HeroSize)
                        .clip(Squircle)
                        .background(Color(console.metaSystemID.color()))
                        .pointerInput(console.metaSystemID, selected) {
                            detectTapGestures(
                                onTap = {
                                    if (selected) onConfirm() else onSelect(index)
                                },
                            )
                        },
                contentAlignment = Alignment.Center,
            ) {
                if (installing == console.metaSystemID) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = Color.White,
                        strokeWidth = 3.dp,
                    )
                } else {
                    Image(
                        painter = painterResource(id = console.metaSystemID.imageResId),
                        contentDescription = stringResource(id = console.metaSystemID.titleResId),
                        modifier = Modifier.fillMaxSize(0.82f),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

@Composable
private fun consoleStatus(
    console: ConsoleItem,
    installing: Boolean,
): String {
    return when {
        installing -> stringResource(R.string.library_scanning)
        console.installed -> stringResource(R.string.console_installed)
        else -> stringResource(R.string.console_not_added)
    }
}

@Composable
private fun ConsoleDetails(
    console: ConsoleItem,
    installing: Boolean,
    accent: Color,
    enabled: Boolean,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val title = remember(console.metaSystemID) { context.getString(console.metaSystemID.titleResId) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Surface(
            onClick = onInstall,
            enabled = enabled,
            modifier =
                Modifier
                    .padding(top = 12.dp)
                    .widthIn(min = 148.dp)
                    .controllerFocusGlow(ButtonShape),
            shape = ButtonShape,
            color = if (enabled) accent else accent.copy(alpha = 0.32f),
            contentColor = Color.White,
            shadowElevation = if (enabled) 8.dp else 0.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        ) {
            Text(
                text =
                    stringResource(
                        if (console.installed) R.string.console_installed else R.string.install_console,
                    ),
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun Modifier.centerGlow(accent: Color): Modifier {
    return drawBehind {
        val center = Offset(size.width / 2f, size.height / 2f - CarouselLift.toPx())
        val radius = size.minDimension * 0.62f
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            accent.copy(alpha = 0.55f),
                            accent.copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                    center = center,
                    radius = radius,
                ),
            radius = radius,
            center = center,
        )
    }
}
