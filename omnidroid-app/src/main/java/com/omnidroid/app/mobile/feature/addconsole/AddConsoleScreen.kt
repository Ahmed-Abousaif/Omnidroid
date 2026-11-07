package com.omnidroid.app.mobile.feature.addconsole

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.addconsole.AddConsoleViewModel.BusyAction
import com.omnidroid.app.mobile.feature.addconsole.AddConsoleViewModel.ConsoleItem
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.mobile.shared.compose.ui.homeChromeBackground
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

private val TileCorner = 14.dp
private val ActionShape = RoundedCornerShape(8.dp)
private val DetailBoxShape = RoundedCornerShape(12.dp)
private val PortraitMaxRotation = 28f
private const val MaxOverscroll = 0.35f
private const val OverscrollResistance = 0.35f
private const val FlingProjectionSeconds = 0.28f
private const val MaxFlingItemsPerSecond = 40f

private enum class ConfirmAction {
    DELETE_DATABASE,
    REMOVE_CONSOLE,
}

@Stable
private class ConsolesCarouselState(initialIndex: Int = 0) {
    var position by mutableFloatStateOf(initialIndex.toFloat())
        private set

    var targetIndex by mutableIntStateOf(initialIndex)
        private set

    var itemCount: Int = 0

    private var settleJob: Job? = null

    private val lastIndex: Int get() = (itemCount - 1).coerceAtLeast(0)

    val selectedIndex: Int get() = position.roundToInt().coerceIn(0, lastIndex)

    fun focusOf(index: Int): Float = 1f - abs(index - position).coerceAtMost(1f)

    fun dragBy(deltaItems: Float) {
        settleJob?.cancel()
        val outside = position < 0f || position > lastIndex
        val delta = if (outside) deltaItems * OverscrollResistance else deltaItems
        position = (position + delta).coerceIn(-MaxOverscroll, lastIndex + MaxOverscroll)
    }

    fun fling(
        scope: CoroutineScope,
        velocityItems: Float,
    ) {
        val velocity = velocityItems.coerceIn(-MaxFlingItemsPerSecond, MaxFlingItemsPerSecond)
        animateTo(scope, (position + velocity * FlingProjectionSeconds).roundToInt(), velocity)
    }

    fun animateTo(
        scope: CoroutineScope,
        index: Int,
        initialVelocity: Float = 0f,
    ) {
        settleJob?.cancel()
        val target = index.coerceIn(0, lastIndex)
        targetIndex = target
        val start = position
        settleJob =
            scope.launch {
                animate(
                    initialValue = start,
                    targetValue = target.toFloat(),
                    initialVelocity = initialVelocity,
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 320f),
                ) { value, _ -> position = value }
            }
    }

    fun clampToCount() {
        if (itemCount <= 0) return
        if (position > lastIndex) {
            settleJob?.cancel()
            position = lastIndex.toFloat()
            targetIndex = lastIndex
        }
    }

    fun settledAmount(): Float {
        val p = position
        return 1f - (abs(p - p.roundToInt()) * 2.5f).coerceIn(0f, 1f)
    }
}

@Composable
fun AddConsoleScreen(
    modifier: Modifier = Modifier,
    viewModel: AddConsoleViewModel,
) {
    val state = viewModel.state.collectAsState().value
    val snackbarHostState = remember { SnackbarHostState() }
    val consoles = state.consoles
    val carousel = remember { ConsolesCarouselState() }
    carousel.itemCount = consoles.size
    LaunchedEffect(consoles.size) { carousel.clampToCount() }

    val scope = rememberCoroutineScope()
    val selectedIndex by remember(carousel) { derivedStateOf { carousel.selectedIndex } }
    val selected = consoles.getOrNull(selectedIndex)
    val focusRequester = remember { FocusRequester() }
    val navigation = LocalControllerNavigation.current
    val configuration = LocalConfiguration.current
    val portrait = configuration.screenHeightDp >= configuration.screenWidthDp
    var pendingConfirm by remember { mutableStateOf<ConfirmAction?>(null) }

    val pulse =
        rememberInfiniteTransition(label = "consolesGlow").animateFloat(
            initialValue = 0.8f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "consolesGlowPulse",
        )

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeError()
    }

    LaunchedEffect(consoles.size) {
        runCatching { focusRequester.requestFocus() }
    }

    navigation?.onCarouselPrev = {
        if (carousel.targetIndex > 0) carousel.animateTo(scope, carousel.targetIndex - 1)
    }
    navigation?.onCarouselNext = {
        if (carousel.targetIndex < consoles.size - 1) carousel.animateTo(scope, carousel.targetIndex + 1)
    }
    navigation?.onCarouselConfirm = {
        val console = consoles.getOrNull(carousel.selectedIndex)
        if (console != null &&
            state.busyMetaSystemID == null &&
            !console.coreInstalled &&
            !console.registeredOrHasGames
        ) {
            viewModel.install(console.metaSystemID)
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .homeChromeBackground()
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (event.type != KeyEventType.KeyDown || carousel.targetIndex <= 0) return@onKeyEvent false
                            carousel.animateTo(scope, carousel.targetIndex - 1)
                            true
                        }
                        Key.DirectionRight -> {
                            if (event.type != KeyEventType.KeyDown || carousel.targetIndex >= consoles.size - 1) {
                                return@onKeyEvent false
                            }
                            carousel.animateTo(scope, carousel.targetIndex + 1)
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA -> {
                            if (event.type == KeyEventType.KeyUp) {
                                navigation?.onCarouselConfirm?.invoke()
                            }
                            true
                        }
                        else -> false
                    }
                }
                .focusable(),
    ) {
        if (consoles.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            return@Box
        }

        if (portrait) {
            PortraitConsolesCarousel(
                state = carousel,
                consoles = consoles,
                selectedIndex = selectedIndex,
                busyMeta = state.busyMetaSystemID,
                pulse = pulse,
                onSelect = { carousel.animateTo(scope, it) },
                detail = {
                    selected?.let { console ->
                        ConsoleDetailPanel(
                            console = console,
                            busy = state.busyMetaSystemID == console.metaSystemID,
                            busyAction = state.busyAction,
                            onInstall = { viewModel.install(console.metaSystemID) },
                            onRefetchDatabase = { viewModel.refetchDatabase(console.metaSystemID) },
                            onDeleteDatabase = { pendingConfirm = ConfirmAction.DELETE_DATABASE },
                            onRemoveConsole = { pendingConfirm = ConfirmAction.REMOVE_CONSOLE },
                            compact = true,
                            boxed = true,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .graphicsLayer { alpha = carousel.settledAmount() },
                        )
                    }
                },
            )
        } else {
            LandscapeConsolesCarousel(
                state = carousel,
                consoles = consoles,
                selectedIndex = selectedIndex,
                busyMeta = state.busyMetaSystemID,
                pulse = pulse,
                onSelect = { carousel.animateTo(scope, it) },
                detail = {
                    selected?.let { console ->
                        ConsoleDetailPanel(
                            console = console,
                            busy = state.busyMetaSystemID == console.metaSystemID,
                            busyAction = state.busyAction,
                            onInstall = { viewModel.install(console.metaSystemID) },
                            onRefetchDatabase = { viewModel.refetchDatabase(console.metaSystemID) },
                            onDeleteDatabase = { pendingConfirm = ConfirmAction.DELETE_DATABASE },
                            onRemoveConsole = { pendingConfirm = ConfirmAction.REMOVE_CONSOLE },
                            compact = true,
                            boxed = true,
                            sideBySideActions = true,
                            modifier =
                                Modifier.graphicsLayer {
                                    alpha = carousel.settledAmount()
                                    translationY = (1f - carousel.settledAmount()) * 8.dp.toPx()
                                },
                        )
                    }
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 76.dp),
        )

        val confirm = pendingConfirm
        if (confirm != null && selected != null) {
            val title =
                stringResource(
                    if (confirm == ConfirmAction.DELETE_DATABASE) {
                        R.string.consoles_delete_database_title
                    } else {
                        R.string.consoles_remove_console_title
                    },
                )
            val name = stringResource(selected.metaSystemID.titleResId)
            val message =
                stringResource(
                    when {
                        confirm == ConfirmAction.DELETE_DATABASE -> R.string.consoles_delete_database_message
                        selected.coreBundled -> R.string.consoles_remove_console_bundled_message
                        else -> R.string.consoles_remove_console_message
                    },
                    name,
                )
            AlertDialog(
                onDismissRequest = { pendingConfirm = null },
                title = { Text(title) },
                text = { Text(message) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingConfirm = null
                            when (confirm) {
                                ConfirmAction.DELETE_DATABASE -> viewModel.deleteDatabase(selected.metaSystemID)
                                ConfirmAction.REMOVE_CONSOLE -> viewModel.removeConsole(selected.metaSystemID)
                            }
                        },
                    ) {
                        Text(
                            stringResource(
                                if (confirm == ConfirmAction.DELETE_DATABASE) {
                                    R.string.consoles_confirm_delete
                                } else {
                                    R.string.consoles_confirm_remove
                                },
                            ),
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingConfirm = null }) {
                        Text(stringResource(R.string.consoles_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun PortraitConsolesCarousel(
    state: ConsolesCarouselState,
    consoles: List<ConsoleItem>,
    selectedIndex: Int,
    busyMeta: com.omnidroid.lib.library.MetaSystemID?,
    pulse: State<Float>,
    onSelect: (Int) -> Unit,
    detail: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val metrics = remember(maxWidth, maxHeight) { PortraitMetrics(maxWidth, maxHeight) }
        val first = (selectedIndex - 3).coerceAtLeast(0)
        val last = (selectedIndex + 3).coerceAtMost(consoles.size - 1)
        val stepPx = with(LocalDensity.current) { metrics.step.toPx() }

        Layout(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .carouselDrag(state, scope, stepPx),
            content = {
                for (index in first..last) {
                    key(consoles[index].metaSystemID) {
                        ConsoleTile(
                            console = consoles[index],
                            focus = { state.focusOf(index) },
                            pulse = pulse,
                            busy = busyMeta == consoles[index].metaSystemID,
                            onClick = {
                                if (index == selectedIndex && abs(state.position - index) < 0.1f) {
                                    // already selected
                                } else {
                                    onSelect(index)
                                }
                            },
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxWidth()) { detail() }
            },
        ) { measurables, constraints ->
            val p = state.position
            val cardW = metrics.cardWidth.roundToPx()
            val cardH = metrics.cardHeight.roundToPx()
            val left = (constraints.maxWidth - cardW) / 2
            val top = metrics.top.roundToPx()
            val near = cardW * 0.74f
            val far = cardW * 0.34f
            val camera = 10f * density
            val cards = measurables.dropLast(1).map { it.measure(Constraints.fixed(cardW, cardH)) }
            val panel = measurables.last().measure(Constraints(maxWidth = constraints.maxWidth))
            layout(constraints.maxWidth, constraints.maxHeight) {
                cards.forEachIndexed { offset, placeable ->
                    val distance = first + offset - p
                    val magnitude = abs(distance)
                    val nearPart = magnitude.coerceAtMost(1f)
                    val farPart = (magnitude - 1f).coerceAtLeast(0f)
                    placeable.placeWithLayer(left, top, zIndex = -magnitude) {
                        translationX = sign(distance) * (nearPart * near + farPart * far)
                        val scale = (1f - 0.18f * nearPart - 0.08f * farPart).coerceAtLeast(0.55f)
                        scaleX = scale
                        scaleY = scale
                        rotationY = -distance.coerceIn(-1f, 1f) * PortraitMaxRotation
                        cameraDistance = camera
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        alpha = (1f - 0.35f * farPart).coerceIn(0.25f, 1f)
                    }
                }
                panel.place(
                    (constraints.maxWidth - panel.width) / 2,
                    top + cardH + metrics.panelGap.roundToPx(),
                )
            }
        }
    }
}

private class PortraitMetrics(maxWidth: Dp, maxHeight: Dp) {
    val panelGap = 14.dp
    val panelArea = 156.dp
    val cardWidth = min(maxWidth * 0.48f, (maxHeight - 28.dp - panelArea)).coerceAtLeast(88.dp)
    val cardHeight = cardWidth
    val top = ((maxHeight - cardHeight - panelArea) / 2).coerceAtLeast(8.dp)
    val step = cardWidth * 0.74f
}

private class LandscapeMetrics(maxWidth: Dp, maxHeight: Dp) {
    val edge = 16.dp
    val gap = 10.dp
    val bigHeight = (maxHeight * 0.52f).coerceIn(96.dp, 200.dp)
    val bigWidth = bigHeight
    val smallHeight = bigHeight * 0.58f
    val smallWidth = smallHeight
    // Lift carousel so the detail box fits under the smaller neighbor tiles.
    val top = ((maxHeight - bigHeight) / 2 - 28.dp).coerceAtLeast(6.dp)
    val anchorX = edge + smallWidth + gap
    val infoX = anchorX + bigWidth + gap * 1.5f
    val infoWidth = (maxWidth - infoX - edge).coerceAtLeast(160.dp)
    // Sit below the side console icons (smaller tiles share the same top as the selected tile).
    val infoY = top + smallHeight + gap
    val infoHeight = (maxHeight - infoY - edge).coerceIn(110.dp, 200.dp)
    val step = smallWidth + gap
    val visibleAfter = ceil(((maxWidth - infoX) / step).coerceAtLeast(0f)).toInt() + 2
}

@Composable
private fun LandscapeConsolesCarousel(
    state: ConsolesCarouselState,
    consoles: List<ConsoleItem>,
    selectedIndex: Int,
    busyMeta: com.omnidroid.lib.library.MetaSystemID?,
    pulse: State<Float>,
    onSelect: (Int) -> Unit,
    detail: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val metrics = remember(maxWidth, maxHeight) { LandscapeMetrics(maxWidth, maxHeight) }
        val first = (selectedIndex - 2).coerceAtLeast(0)
        val last = (selectedIndex + metrics.visibleAfter).coerceAtMost(consoles.size - 1)
        val stepPx = with(LocalDensity.current) { metrics.step.toPx() }

        Layout(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .carouselDrag(state, scope, stepPx),
            content = {
                for (index in first..last) {
                    key(consoles[index].metaSystemID) {
                        ConsoleTile(
                            console = consoles[index],
                            focus = { state.focusOf(index) },
                            pulse = pulse,
                            busy = busyMeta == consoles[index].metaSystemID,
                            onClick = {
                                if (index != selectedIndex || abs(state.position - index) >= 0.1f) {
                                    onSelect(index)
                                }
                            },
                        )
                    }
                }
                Box { detail() }
            },
        ) { measurables, constraints ->
            val p = state.position
            val bigW = metrics.bigWidth.toPx()
            val smallW = metrics.smallWidth.toPx()
            val gap = metrics.gap.toPx()
            val anchorX = metrics.anchorX.toPx()
            val top = metrics.top.roundToPx()
            val base = floor(p).toInt()
            val fraction = p - base

            fun focusAt(index: Int) = 1f - abs(index - p).coerceAtMost(1f)

            fun widthOf(index: Int) = smallW + (bigW - smallW) * focusAt(index)

            fun xOf(index: Int): Float {
                var x = anchorX - fraction * (widthOf(base) + gap)
                if (index >= base) {
                    for (k in base until index) x += widthOf(k) + gap
                } else {
                    for (k in base - 1 downTo index) x -= widthOf(k) + gap
                }
                return x
            }

            val cards =
                measurables.dropLast(1).mapIndexed { offset, measurable ->
                    val index = first + offset
                    val width = widthOf(index)
                    index to measurable.measure(Constraints.fixed(width.roundToInt(), width.roundToInt()))
                }
            val info =
                measurables.last().measure(
                    Constraints(
                        maxWidth = metrics.infoWidth.roundToPx(),
                        maxHeight = metrics.infoHeight.roundToPx(),
                    ),
                )
            layout(constraints.maxWidth, constraints.maxHeight) {
                cards.forEach { (index, placeable) ->
                    placeable.place(xOf(index).roundToInt(), top, zIndex = focusAt(index))
                }
                // Wide detail box to the right of the selected tile, below the smaller neighbor icons.
                info.place(
                    metrics.infoX.roundToPx(),
                    metrics.infoY.roundToPx(),
                )
            }
        }
    }
}

private fun Modifier.carouselDrag(
    state: ConsolesCarouselState,
    scope: CoroutineScope,
    stepPx: Float,
): Modifier =
    pointerInput(state, stepPx) {
        val tracker = VelocityTracker()
        detectHorizontalDragGestures(
            onDragStart = { tracker.resetTracking() },
            onDragEnd = { state.fling(scope, -tracker.calculateVelocity().x / stepPx) },
            onDragCancel = { state.fling(scope, 0f) },
        ) { change, dragAmount ->
            tracker.addPosition(change.uptimeMillis, change.position)
            change.consume()
            state.dragBy(-dragAmount / stepPx)
        }
    }

@Composable
private fun ConsoleTile(
    console: ConsoleItem,
    focus: () -> Float,
    pulse: State<Float>,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val tileShape = RoundedCornerShape(TileCorner)
    val accent = Color(console.metaSystemID.color())
    val installed = console.coreInstalled || console.registeredOrHasGames
    val tileColor = if (installed) accent else chromeColors.panelSelected
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val f = focus()
                    val glow = if (installed) f * pulse.value else f * pulse.value * 0.35f
                    shadowElevation = 4.dp.toPx() + glow * 10.dp.toPx()
                    shape = tileShape
                    clip = false
                }
                .clip(tileShape)
                .background(tileColor)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = if (installed) Color.White else chromeColors.muted,
                strokeWidth = 3.dp,
            )
        } else {
            Image(
                painter = painterResource(id = console.metaSystemID.imageResId),
                contentDescription = stringResource(id = console.metaSystemID.titleResId),
                modifier =
                    Modifier
                        .fillMaxSize(0.78f)
                        .graphicsLayer {
                            val f = focus()
                            alpha =
                                if (installed) {
                                    0.55f + 0.45f * f
                                } else {
                                    0.28f + 0.22f * f
                                }
                        },
                contentScale = ContentScale.Fit,
                colorFilter =
                    if (installed) {
                        null
                    } else {
                        ColorFilter.tint(chromeColors.muted)
                    },
            )
        }
    }
}

@Composable
private fun ConsoleDetailPanel(
    console: ConsoleItem,
    busy: Boolean,
    busyAction: BusyAction?,
    onInstall: () -> Unit,
    onRefetchDatabase: () -> Unit,
    onDeleteDatabase: () -> Unit,
    onRemoveConsole: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    boxed: Boolean = false,
    sideBySideActions: Boolean = false,
) {
    val context = LocalContext.current
    val title = remember(console.metaSystemID) { context.getString(console.metaSystemID.titleResId) }
    val needsInstall = !console.coreInstalled && !console.registeredOrHasGames
    val canManage = console.coreInstalled || console.registeredOrHasGames || console.databaseInstalled
    val titleStyle =
        if (compact) {
            MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        } else {
            MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        }
    val bodyStyle =
        if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium

    val content: @Composable () -> Unit = {
        Column(
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
        ) {
            Text(
                text = title,
                style = titleStyle,
                color = chromeColors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.consoles_your_games, console.libraryGameCount),
                style = bodyStyle,
                color = chromeColors.muted,
            )
            Text(
                text =
                    if (console.databaseInstalled) {
                        stringResource(R.string.consoles_database_games, console.databaseGameCount)
                    } else {
                        stringResource(R.string.consoles_database_missing)
                    },
                style = bodyStyle,
                color = chromeColors.muted,
            )
            Text(
                text =
                    when {
                        console.coreDisplayName == null -> stringResource(R.string.consoles_core_missing)
                        console.coreBundled ->
                            stringResource(R.string.consoles_core_bundled, console.coreDisplayName)
                        console.coreInstalled ->
                            stringResource(R.string.consoles_core_label, console.coreDisplayName)
                        else -> stringResource(R.string.consoles_core_missing)
                    },
                style = bodyStyle,
                color = chromeColors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(if (compact) 6.dp else 10.dp))

            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(if (compact) 14.dp else 22.dp),
                        strokeWidth = if (compact) 2.dp else 2.5.dp,
                        color = LibraryNeonGreen,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text =
                            when (busyAction) {
                                BusyAction.INSTALL -> stringResource(R.string.install_console)
                                BusyAction.REFETCH_DATABASE -> stringResource(R.string.consoles_refetch_database)
                                BusyAction.DELETE_DATABASE -> stringResource(R.string.consoles_delete_database)
                                BusyAction.REMOVE_CONSOLE -> stringResource(R.string.consoles_remove_console)
                                null -> ""
                            },
                        style = bodyStyle,
                        color = chromeColors.muted,
                    )
                }
            } else if (needsInstall) {
                ActionButton(
                    label = stringResource(R.string.install_console),
                    accent = Color(console.metaSystemID.color()),
                    onClick = onInstall,
                    primary = true,
                    compact = compact,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (canManage) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (sideBySideActions) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            ActionButton(
                                label =
                                    stringResource(
                                        if (console.databaseInstalled) {
                                            R.string.consoles_refetch_database
                                        } else {
                                            R.string.consoles_download_database
                                        },
                                    ),
                                accent = Color(console.metaSystemID.color()),
                                onClick = onRefetchDatabase,
                                primary = true,
                                compact = compact,
                                modifier = Modifier.weight(1f),
                            )
                            if (console.databaseInstalled) {
                                ActionButton(
                                    label = stringResource(R.string.consoles_delete_database),
                                    accent = chromeColors.panelSelected,
                                    onClick = onDeleteDatabase,
                                    primary = false,
                                    compact = compact,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        ActionButton(
                            label = stringResource(R.string.consoles_remove_console),
                            accent = Color(0xFF8B3A3A),
                            onClick = onRemoveConsole,
                            primary = false,
                            compact = compact,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        ActionButton(
                            label =
                                stringResource(
                                    if (console.databaseInstalled) {
                                        R.string.consoles_refetch_database
                                    } else {
                                        R.string.consoles_download_database
                                    },
                                ),
                            accent = Color(console.metaSystemID.color()),
                            onClick = onRefetchDatabase,
                            primary = true,
                            compact = compact,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (console.databaseInstalled) {
                            ActionButton(
                                label = stringResource(R.string.consoles_delete_database),
                                accent = chromeColors.panelSelected,
                                onClick = onDeleteDatabase,
                                primary = false,
                                compact = compact,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        ActionButton(
                            label = stringResource(R.string.consoles_remove_console),
                            accent = Color(0xFF8B3A3A),
                            onClick = onRemoveConsole,
                            primary = false,
                            compact = compact,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (boxed) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = DetailBoxShape,
            color = chromeColors.panel.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, chromeColors.panelBorder),
            shadowElevation = 6.dp,
        ) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                content()
            }
        }
    } else {
        Column(
            modifier = modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .heightIn(min = if (compact) 28.dp else 40.dp)
                .controllerFocusGlow(ActionShape),
        shape = ActionShape,
        color = if (primary) accent else accent.copy(alpha = 0.9f),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = if (primary && !compact) 6.dp else 0.dp,
    ) {
        Text(
            text = label,
            modifier =
                Modifier.padding(
                    horizontal = if (compact) 10.dp else 14.dp,
                    vertical = if (compact) 5.dp else 10.dp,
                ),
            style =
                (if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge)
                    .copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
