package com.omnidroid.app.mobile.feature.library

import android.os.Build
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.paging.compose.LazyPagingItems
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.HomeChromeBackground
import com.omnidroid.app.mobile.shared.compose.ui.LibraryGameCardAspectRatio
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.compose.ui.OmnidroidGameImage
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.app.utils.games.GameUtils
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.library.metaSystemID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

private val ConsoleCardColor = Color(0xFF161616)
private val LandscapeCardCorner = 10.dp
private val PortraitCardCorner = 18.dp
private val ConsoleActionHeight = 40.dp
private val ConsoleActionShape = RoundedCornerShape(8.dp)
private val ConsoleStartMinWidth = 168.dp
private val GlowSpread = 14.dp
private const val GlowLayers = 14
private const val GlowLayerAlpha = 0.016f
private const val PortraitMaxRotation = 32f
private const val MaxOverscroll = 0.35f
private const val OverscrollResistance = 0.35f
private const val FlingProjectionSeconds = 0.28f
private const val MaxFlingItemsPerSecond = 40f

@Stable
internal class ConsoleCarouselState(initialIndex: Int) {
    var position by mutableFloatStateOf(initialIndex.toFloat())
        private set

    var targetIndex by mutableIntStateOf(initialIndex)
        private set

    // Only read by gesture and key handlers, so it does not need to be observable.
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
        if (itemCount > 0 && position > lastIndex) {
            settleJob?.cancel()
            position = lastIndex.toFloat()
            targetIndex = lastIndex
        }
    }
}

/** Selected carousel index, hoisted above the orientation branches so it survives switching. */
@Composable
internal fun rememberConsoleSelection(key: Any?): MutableIntState = rememberSaveable(key) { mutableIntStateOf(0) }

@Composable
private fun rememberConsoleCarouselState(selection: MutableIntState): ConsoleCarouselState {
    val state = remember(selection) { ConsoleCarouselState(selection.intValue) }
    LaunchedEffect(state) {
        snapshotFlow { state.targetIndex }.collect { index ->
            if (state.itemCount > 0) selection.intValue = index
        }
    }
    return state
}

/** Paged games with the optional "continue" game pinned in front. */
private class ConsoleItems(
    private val games: LazyPagingItems<Game>,
    private val featured: Game?,
) {
    private val offset = if (featured != null) 1 else 0

    val count: Int get() = games.itemCount + offset

    fun isFeatured(index: Int) = featured != null && index == 0

    fun gameAt(index: Int): Game? {
        if (index !in 0 until count) return null
        return if (isFeatured(index)) featured else games[index - offset]
    }

    fun keyAt(index: Int): Any =
        if (isFeatured(index)) {
            "continue-${featured?.id}"
        } else {
            games.peek(index - offset)?.id ?: "placeholder-$index"
        }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ConsoleLibrary(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    games: LazyPagingItems<Game>,
    featured: Game?,
    selection: MutableIntState,
    portrait: Boolean,
    showControllerGlyph: Boolean,
    focusRequester: FocusRequester,
    onOpenGame: (Game) -> Unit,
    onPlayGame: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onFavoriteToggle: (Game, Boolean) -> Unit,
    onBackdropGameChange: (Game?) -> Unit,
) {
    val items = ConsoleItems(games, featured)
    val state = rememberConsoleCarouselState(selection)
    state.itemCount = items.count
    val count = items.count
    LaunchedEffect(state, count) { state.clampToCount() }

    val scope = rememberCoroutineScope()
    val selectedIndex by remember(state) { derivedStateOf { state.selectedIndex } }
    val selectedGame = items.gameAt(selectedIndex)
    val pulse =
        rememberInfiniteTransition(label = "consoleGlow").animateFloat(
            initialValue = 0.8f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "consoleGlowPulse",
        )

    val navigation = LocalControllerNavigation.current
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(hasFocus, selectedGame?.id) {
        if (hasFocus) navigation?.setFocusedGame(selectedGame)
    }

    val currentOnBackdropGameChange by rememberUpdatedState(onBackdropGameChange)
    LaunchedEffect(selectedGame?.id) {
        delay(160)
        currentOnBackdropGameChange(selectedGame)
    }
    DisposableEffect(Unit) {
        onDispose { currentOnBackdropGameChange(null) }
    }

    val onCardClick: (Int, Game) -> Unit = { index, game ->
        if (index == selectedIndex && abs(state.position - index) < 0.1f) {
            onOpenGame(game)
        } else {
            state.animateTo(scope, index)
        }
    }
    val playSelected: () -> Unit = {
        items.gameAt(state.selectedIndex)?.let(onPlayGame)
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    hasFocus = focusState.isFocused
                    if (!focusState.isFocused) navigation?.setFocusedGame(null)
                }
                .onKeyEvent { event -> handleCarouselKey(event, state, scope, playSelected) }
                .focusable(),
    ) {
        if (portrait) {
            ConsolePortraitCarousel(
                state = state,
                items = items,
                selectedIndex = selectedIndex,
                pulse = pulse,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                showControllerGlyph = showControllerGlyph,
                onCardClick = onCardClick,
                onGameLongClick = onGameLongClick,
                onFavoriteToggle = onFavoriteToggle,
                onPlay = playSelected,
            )
        } else {
            ConsoleLandscapeCarousel(
                state = state,
                items = items,
                selectedIndex = selectedIndex,
                pulse = pulse,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                showControllerGlyph = showControllerGlyph,
                onCardClick = onCardClick,
                onGameLongClick = onGameLongClick,
                onFavoriteToggle = onFavoriteToggle,
                onPlay = playSelected,
            )
        }
    }
}

private fun handleCarouselKey(
    event: KeyEvent,
    state: ConsoleCarouselState,
    scope: CoroutineScope,
    onConfirm: () -> Unit,
): Boolean {
    return when (event.key) {
        // At either end the key is left unconsumed so focus can move on to the console rail.
        Key.DirectionLeft -> {
            if (event.type != KeyEventType.KeyDown || state.targetIndex <= 0) return false
            state.animateTo(scope, state.targetIndex - 1)
            true
        }
        Key.DirectionRight -> {
            if (event.type != KeyEventType.KeyDown || state.targetIndex >= state.itemCount - 1) return false
            state.animateTo(scope, state.targetIndex + 1)
            true
        }
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA -> {
            if (event.type == KeyEventType.KeyUp) onConfirm()
            true
        }
        else -> false
    }
}

private fun Modifier.carouselDrag(
    state: ConsoleCarouselState,
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

/** How settled the carousel is on a single item: 1 when resting, 0 halfway between two. */
private fun ConsoleCarouselState.settledAmount(): Float {
    val p = position
    return 1f - (abs(p - p.roundToInt()) * 2.5f).coerceIn(0f, 1f)
}

private class LandscapeMetrics(maxWidth: Dp, maxHeight: Dp) {
    val edge = if (maxHeight < 280.dp) 16.dp else 20.dp
    val gap = 12.dp
    val bigHeight = (maxHeight - edge * 2).coerceIn(96.dp, 440.dp)
    val bigWidth = bigHeight * LibraryGameCardAspectRatio
    val smallHeight = bigHeight * 0.55f
    val smallWidth = smallHeight * LibraryGameCardAspectRatio
    val top = ((maxHeight - bigHeight) / 2).coerceAtLeast(0.dp)
    val anchorX = edge + smallWidth + gap
    val infoX = anchorX + bigWidth + gap * 1.5f
    val infoWidth = (maxWidth - infoX - edge).coerceAtLeast(0.dp)
    val infoHeight = (bigHeight - smallHeight - gap).coerceAtLeast(0.dp)
    val compactInfo = infoHeight < 110.dp
    val step = smallWidth + gap
    val visibleAfter = ceil(((maxWidth - infoX) / step).coerceAtLeast(0f)).toInt() + 2
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ConsoleLandscapeCarousel(
    state: ConsoleCarouselState,
    items: ConsoleItems,
    selectedIndex: Int,
    pulse: State<Float>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    showControllerGlyph: Boolean,
    onCardClick: (Int, Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onFavoriteToggle: (Game, Boolean) -> Unit,
    onPlay: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val metrics = remember(maxWidth, maxHeight) { LandscapeMetrics(maxWidth, maxHeight) }
        val first = (selectedIndex - 2).coerceAtLeast(0)
        val last = (selectedIndex + metrics.visibleAfter).coerceAtMost(items.count - 1)
        val stepPx = with(LocalDensity.current) { metrics.step.toPx() }

        Layout(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .carouselDrag(state, scope, stepPx),
            content = {
                for (index in first..last) {
                    key(items.keyAt(index)) {
                        val game = items.gameAt(index)
                        ConsoleLandscapeCard(
                            game = game,
                            renderWidth = metrics.bigWidth,
                            renderHeight = metrics.bigHeight,
                            focus = { state.focusOf(index) },
                            pulse = pulse,
                            sharedModifier =
                                game?.let {
                                    consoleCoverSharedBounds(sharedTransitionScope, animatedVisibilityScope, it, LandscapeCardCorner)
                                } ?: Modifier,
                            onClick = { game?.let { onCardClick(index, it) } },
                            onLongClick = { game?.let(onGameLongClick) },
                        )
                    }
                }
                LandscapeInfoPanel(
                    game = items.gameAt(selectedIndex),
                    continueAction = items.isFeatured(selectedIndex),
                    compact = metrics.compactInfo,
                    showControllerGlyph = showControllerGlyph,
                    modifier =
                        Modifier.graphicsLayer {
                            val settled = state.settledAmount()
                            alpha = settled
                            translationY = (1f - settled) * 10.dp.toPx()
                        },
                    onPlay = onPlay,
                    onFavoriteToggle = onFavoriteToggle,
                    onSettings = onGameLongClick,
                )
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

            // Walk outward from the item just left of the scroll position so neighbours stay
            // packed edge to edge while the selected card grows and shrinks.
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
                    val height = width / LibraryGameCardAspectRatio
                    index to measurable.measure(Constraints.fixed(width.roundToInt(), height.roundToInt()))
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
                info.place(
                    metrics.infoX.roundToPx(),
                    top + metrics.bigHeight.roundToPx() - info.height,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConsoleLandscapeCard(
    game: Game?,
    renderWidth: Dp,
    renderHeight: Dp,
    focus: () -> Float,
    pulse: State<Float>,
    sharedModifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(LandscapeCardCorner)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .consoleSelectionGlow(LandscapeCardCorner, focus, pulse)
                .then(sharedModifier)
                .shadow(elevation = 6.dp, shape = shape)
                .background(ConsoleCardColor)
                .focusProperties { canFocus = false }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (game == null) return@Box
        OmnidroidGameImage(
            modifier = Modifier.renderAtSize(renderWidth, renderHeight),
            game = game,
            aspectRatio = null,
        )
        ConsoleSystemBanner(
            game = game,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { alpha = 1f - focus() },
        )
    }
}

private const val TitleRotateDelayMillis = 1500L

/** Ellipsized title that, once [active] for a moment, scrolls on one line so the full name can be read. */
@Composable
private fun ConsoleRotatingTitle(
    text: String,
    style: TextStyle,
    maxLines: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    var overflowing by remember(text) { mutableStateOf(false) }
    var rotating by remember(text) { mutableStateOf(false) }
    var restingHeight by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text, active, overflowing) {
        rotating = false
        if (active && overflowing) {
            delay(TitleRotateDelayMillis)
            rotating = true
        }
    }
    if (rotating) {
        // Keeps the wrapped height so dropping to one scrolling line doesn't shift the layout.
        Box(
            modifier = modifier.heightIn(min = with(LocalDensity.current) { restingHeight.toDp() }),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                text = text,
                style = style,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 0),
            )
        }
    } else {
        Text(
            text = text,
            style = style,
            color = Color.White,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = {
                overflowing = it.hasVisualOverflow
                restingHeight = it.size.height
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun LandscapeInfoPanel(
    game: Game?,
    continueAction: Boolean,
    compact: Boolean,
    showControllerGlyph: Boolean,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onFavoriteToggle: (Game, Boolean) -> Unit,
    onSettings: (Game) -> Unit,
) {
    if (game == null) return
    val context = LocalContext.current
    val subtitle =
        remember(game.id, game.systemId, game.developer) {
            GameUtils.getGameSubtitle(context, game).trim()
        }
    Column(modifier = modifier, verticalArrangement = Arrangement.Bottom) {
        // Weighted so a long title gives up lines instead of squeezing the action buttons.
        ConsoleRotatingTitle(
            text = game.displayName,
            style =
                (if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall)
                    .copy(fontWeight = FontWeight.Bold),
            maxLines = if (compact) 1 else 2,
            active = true,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFBDBDBD),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(if (compact) 6.dp else 10.dp))
        ConsoleActions(
            label = stringResource(if (continueAction) R.string.continue_playing else R.string.library_console_start),
            showControllerGlyph = showControllerGlyph,
            game = game,
            onPlay = onPlay,
            onFavoriteToggle = onFavoriteToggle,
            onSettings = { onSettings(game) },
        )
    }
}

private class PortraitMetrics(maxWidth: Dp, maxHeight: Dp) {
    val buttonGap = 20.dp
    val buttonArea = 40.dp + buttonGap
    val cardWidth = min(maxWidth * 0.68f, (maxHeight - 32.dp - buttonArea) * LibraryGameCardAspectRatio).coerceAtLeast(80.dp)
    val cardHeight = cardWidth / LibraryGameCardAspectRatio
    val top = ((maxHeight - cardHeight - buttonArea) / 2).coerceAtLeast(0.dp)
    val step = cardWidth * 0.74f
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ConsolePortraitCarousel(
    state: ConsoleCarouselState,
    items: ConsoleItems,
    selectedIndex: Int,
    pulse: State<Float>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    showControllerGlyph: Boolean,
    onCardClick: (Int, Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onFavoriteToggle: (Game, Boolean) -> Unit,
    onPlay: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val metrics = remember(maxWidth, maxHeight) { PortraitMetrics(maxWidth, maxHeight) }
        val first = (selectedIndex - 3).coerceAtLeast(0)
        val last = (selectedIndex + 3).coerceAtMost(items.count - 1)
        val stepPx = with(LocalDensity.current) { metrics.step.toPx() }

        Layout(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .carouselDrag(state, scope, stepPx),
            content = {
                for (index in first..last) {
                    key(items.keyAt(index)) {
                        val game = items.gameAt(index)
                        ConsolePortraitCard(
                            game = game,
                            focus = { state.focusOf(index) },
                            pulse = pulse,
                            sharedModifier =
                                game?.let {
                                    consoleCoverSharedBounds(sharedTransitionScope, animatedVisibilityScope, it, PortraitCardCorner)
                                } ?: Modifier,
                            onClick = { game?.let { onCardClick(index, it) } },
                            onLongClick = { game?.let(onGameLongClick) },
                        )
                    }
                }
                ConsoleActions(
                    label =
                        stringResource(
                            if (items.isFeatured(selectedIndex)) R.string.continue_playing else R.string.library_console_start,
                        ),
                    showControllerGlyph = showControllerGlyph,
                    modifier = Modifier.graphicsLayer { alpha = state.settledAmount() },
                    game = items.gameAt(selectedIndex),
                    onPlay = onPlay,
                    onFavoriteToggle = onFavoriteToggle,
                    onSettings = { items.gameAt(state.selectedIndex)?.let(onGameLongClick) },
                )
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
            val cards =
                measurables.dropLast(1).map { it.measure(Constraints.fixed(cardW, cardH)) }
            val button = measurables.last().measure(Constraints(maxWidth = constraints.maxWidth))
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
                        // Side cards turn their inner edge away, cover-flow style.
                        rotationY = -distance.coerceIn(-1f, 1f) * PortraitMaxRotation
                        cameraDistance = camera
                        alpha = (2.6f - magnitude).coerceIn(0f, 1f)
                    }
                }
                button.place(
                    (constraints.maxWidth - button.width) / 2,
                    top + cardH + metrics.buttonGap.roundToPx(),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConsolePortraitCard(
    game: Game?,
    focus: () -> Float,
    pulse: State<Float>,
    sharedModifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(PortraitCardCorner)
    val currentFocus by rememberUpdatedState(focus)
    val centered by remember { derivedStateOf { currentFocus() > 0.95f } }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .consoleSelectionGlow(PortraitCardCorner, focus, pulse)
                .then(sharedModifier)
                .shadow(
                    elevation = 20.dp,
                    shape = shape,
                    ambientColor = Color.Black,
                    spotColor = Color.Black,
                )
                .background(ConsoleCardColor)
                .focusProperties { canFocus = false }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (game != null) {
            OmnidroidGameImage(
                modifier = Modifier.fillMaxSize(),
                game = game,
                aspectRatio = null,
            )
            ConsoleSystemChip(
                game = game,
                modifier =
                    Modifier
                        .align(Alignment.TopStart),
            )
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.35f to Color.Black.copy(alpha = 0.7f),
                                1f to Color.Black.copy(alpha = 0.96f),
                            ),
                        )
                        .padding(start = 14.dp, end = 14.dp, top = 56.dp, bottom = 16.dp),
            ) {
                ConsoleRotatingTitle(
                    text = game.displayName.uppercase(),
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            shadow = Shadow(color = Color.Black, offset = Offset(0f, 2f), blurRadius = 8f),
                        ),
                    maxLines = 2,
                    active = centered,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = (1f - focus()) * 0.5f }
                    .background(Color.Black),
        )
    }
}

@Composable
private fun ConsoleSystemBanner(
    game: Game,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val label = remember(game.systemId) { GameUtils.getSystemShortName(context, game) }
    val color = remember(game.systemId) { systemColor(game) }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color)
                .padding(horizontal = 4.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConsoleSystemChip(
    game: Game,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val label = remember(game.systemId) { GameUtils.getSystemShortName(context, game) }
    val color = remember(game.systemId) { systemColor(game) }
    Box(
        modifier =
            modifier
                .background(
                    color = color,
                    shape = RoundedCornerShape(topStart = PortraitCardCorner, bottomEnd = 12.dp),
                )
                .padding(start = 14.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun ConsoleActions(
    label: String,
    showControllerGlyph: Boolean,
    modifier: Modifier = Modifier,
    game: Game?,
    onPlay: () -> Unit,
    onFavoriteToggle: (Game, Boolean) -> Unit,
    onSettings: () -> Unit,
) {
    // Paging only re-emits the game after the database write lands, so flip the heart immediately.
    var favorite by remember(game?.id, game?.isFavorite) { mutableStateOf(game?.isFavorite == true) }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConsoleStartButton(
            label = label,
            showControllerGlyph = showControllerGlyph,
            onClick = onPlay,
        )
        Spacer(modifier = Modifier.width(8.dp))
        ConsoleIconButton(
            imageVector = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(R.string.favorites),
            tint = if (favorite) LibraryNeonGreen else Color.White.copy(alpha = 0.85f),
            onClick = {
                game?.let {
                    favorite = !favorite
                    onFavoriteToggle(it, favorite)
                }
            },
        )
        Spacer(modifier = Modifier.width(8.dp))
        ConsoleIconButton(
            imageVector = Icons.Outlined.Settings,
            contentDescription = stringResource(R.string.game_settings),
            onClick = onSettings,
        )
    }
}

@Composable
private fun ConsoleIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color = Color.White.copy(alpha = 0.85f),
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier =
            Modifier
                .size(ConsoleActionHeight)
                .focusProperties { canFocus = false },
        shape = ConsoleActionShape,
        color = Color(0xFF2A2A2A),
        border = BorderStroke(1.dp, Color(0xFF3A3A3A)),
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = tint,
            )
        }
    }
}

@Composable
private fun ConsoleStartButton(
    label: String,
    showControllerGlyph: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .height(ConsoleActionHeight)
                .widthIn(min = ConsoleStartMinWidth)
                .focusProperties { canFocus = false },
        shape = ConsoleActionShape,
        color = LibraryNeonGreen,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showControllerGlyph) {
                Box(
                    modifier =
                        Modifier
                            .size(20.dp)
                            .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "A",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = Color.White,
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

/** Blurred cover of the selected game, meant to sit behind the whole library chrome. */
@Composable
internal fun ConsoleBackdrop(
    game: Game?,
    modifier: Modifier = Modifier,
) {
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(modifier = modifier) {
        Crossfade(
            targetState = game,
            animationSpec = tween(durationMillis = 450),
            label = "consoleBackdrop",
            modifier = Modifier.fillMaxSize(),
        ) { target ->
            if (target != null) {
                OmnidroidGameImage(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(if (canBlur) Modifier.blur(36.dp) else Modifier)
                            .graphicsLayer { alpha = if (canBlur) 0.5f else 0.2f },
                    game = target,
                    aspectRatio = null,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to HomeChromeBackground.copy(alpha = 0.35f),
                            0.6f to HomeChromeBackground.copy(alpha = 0.65f),
                            1f to HomeChromeBackground.copy(alpha = 0.95f),
                        ),
                    ),
        )
    }
}

/** Extends the content upward by [amount], so it can reach under chrome laid over this area. */
internal fun Modifier.bleedTop(amount: Dp): Modifier =
    layout { measurable, constraints ->
        val extra = amount.roundToPx().coerceAtLeast(0)
        val placeable =
            measurable.measure(
                constraints.copy(
                    minHeight = constraints.minHeight + extra,
                    maxHeight = constraints.maxHeight + extra,
                ),
            )
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(0, -extra)
        }
    }

/** Gentle green halo and outline that fade in as the card becomes selected. */
private fun Modifier.consoleSelectionGlow(
    corner: Dp,
    amount: () -> Float,
    pulse: State<Float>,
): Modifier =
    drawBehind {
        val selected = amount()
        if (selected <= 0.01f) return@drawBehind
        val strength = selected * selected * pulse.value
        val spread = GlowSpread.toPx()
        val radius = corner.toPx()
        for (layer in GlowLayers downTo 1) {
            val grow = spread * layer / GlowLayers
            drawRoundRect(
                color = LibraryNeonGreen.copy(alpha = GlowLayerAlpha * strength),
                topLeft = Offset(-grow, -grow),
                size = Size(size.width + grow * 2, size.height + grow * 2),
                cornerRadius = CornerRadius(radius + grow),
            )
        }
    }.drawWithContent {
        drawContent()
        val selected = amount()
        if (selected <= 0.01f) return@drawWithContent
        val stroke = 1.5.dp.toPx()
        drawRoundRect(
            color = LibraryNeonGreen.copy(alpha = selected),
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(corner.toPx() - stroke / 2),
            style = Stroke(width = stroke),
        )
    }

/**
 * Lays the content out at a fixed size and scales it into the available bounds, so covers are
 * decoded at the selected card's resolution even while the card is small.
 */
private fun Modifier.renderAtSize(
    width: Dp,
    height: Dp,
): Modifier =
    layout { measurable, constraints ->
        val renderWidth = width.roundToPx().coerceAtLeast(1)
        val renderHeight = height.roundToPx().coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(renderWidth, renderHeight))
        val outWidth = constraints.maxWidth
        val outHeight = constraints.maxHeight
        layout(outWidth, outHeight) {
            placeable.placeWithLayer(0, 0) {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = outWidth / renderWidth.toFloat()
                scaleY = outHeight / renderHeight.toFloat()
            }
        }
    }

private fun systemColor(game: Game): Color =
    runCatching { Color(GameSystem.findById(game.systemId).metaSystemID().color()) }
        .getOrDefault(Color(0xFF2E2E2E))

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun consoleCoverSharedBounds(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    game: Game,
    corner: Dp,
): Modifier {
    val cornerAnim by animatedVisibilityScope.transition.animateDp(
        label = "coverCorner-${game.id}",
        transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) },
    ) { targetState ->
        if (targetState == EnterExitState.Visible) corner else 16.dp
    }
    return with(sharedTransitionScope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "game-cover-${game.id}"),
            animatedVisibilityScope = animatedVisibilityScope,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            boundsTransform = { _, _ ->
                tween(durationMillis = 400, easing = FastOutSlowInEasing)
            },
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(cornerAnim)),
        )
    }
}
