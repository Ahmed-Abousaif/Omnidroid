package com.omnidroid.app.mobile.feature.library

import android.content.Context
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.GameCardInfoStyle
import com.omnidroid.app.mobile.shared.compose.ui.HomeChromeBackground
import com.omnidroid.app.mobile.shared.compose.ui.OmnidroidEmptyView
import com.omnidroid.app.mobile.shared.compose.ui.OmnidroidGameCard
import com.omnidroid.app.mobile.shared.compose.ui.LibraryGameCardAspectRatio
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow
import com.omnidroid.lib.library.MetaSystemID
import com.omnidroid.lib.library.db.entity.Game
import kotlin.math.roundToInt

val LibrarySidebarWidth = 80.dp
val LibrarySidebarButtonSize = 48.dp
val LibrarySidebarInset = (LibrarySidebarWidth - LibrarySidebarButtonSize) / 2

fun libraryGridRows(zoomDensity: Int, availableHeight: Dp): Int {
    return when {
        availableHeight < 420.dp -> {
            when (zoomDensity) {
                0 -> 1
                1 -> 2
                else -> 3
            }
        }
        availableHeight < 640.dp -> {
            when (zoomDensity) {
                0 -> 2
                1 -> 3
                else -> 5
            }
        }
        availableHeight < 880.dp -> {
            when (zoomDensity) {
                0 -> 3
                1 -> 5
                else -> 7
            }
        }
        else -> {
            when (zoomDensity) {
                0 -> 3
                1 -> (availableHeight / 140.dp).toInt().coerceIn(5, 6)
                else -> (availableHeight / 90.dp).toInt().coerceIn(7, 9)
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: LibraryViewModel,
    onGameClick: (Game) -> Unit,
    onContinueClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onAddConsole: () -> Unit,
    controllerConnected: Boolean = false,
) {
    val state = viewModel.state.collectAsState().value
    val games = viewModel.games.collectAsLazyPagingItems()

    Row(
        modifier =
            modifier
                .fillMaxSize()
                .background(HomeChromeBackground),
    ) {
        LibrarySidebar(
            modifier = Modifier,
            filter = state.filter,
            systems = state.sidebarSystems,
            onFilterSelected = { viewModel.selectFilter(it) },
            onAddConsole = onAddConsole,
        )
        LibraryGrid(
            modifier = Modifier.weight(1f),
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            state = state,
            games = games,
            onGameClick = onGameClick,
            onContinueClick = onContinueClick,
            onGameLongClick = onGameLongClick,
            onSync = { viewModel.syncLibrary(it) },
            onZoomDensityChange = { viewModel.setZoomDensity(it) },
            requestInitialFocus = controllerConnected,
            showZoomBar = !controllerConnected,
        )
    }
}

@Composable
private fun LibrarySidebar(
    modifier: Modifier = Modifier,
    filter: LibraryFilter,
    systems: List<MetaSystemID>,
    onFilterSelected: (LibraryFilter) -> Unit,
    onAddConsole: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .width(LibrarySidebarWidth)
                .padding(vertical = 12.dp)
                .focusGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SidebarIconButton(
            selected = filter is LibraryFilter.Favorites,
            icon = if (filter is LibraryFilter.Favorites) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(R.string.favorites),
            onClick = { onFilterSelected(LibraryFilter.Favorites) },
        )
        Spacer(modifier = Modifier.height(4.dp))
        SidebarIconButton(
            selected = filter is LibraryFilter.All,
            icon = Icons.Filled.GridView,
            contentDescription = stringResource(R.string.show_all),
            onClick = { onFilterSelected(LibraryFilter.All) },
        )
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(
            modifier = Modifier.width(28.dp),
            color = Color.White.copy(alpha = 0.12f),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            systems.forEach { system ->
                SidebarSystemLogoButton(
                    meta = system,
                    selected = (filter as? LibraryFilter.System)?.metaSystemID == system,
                    onClick = { onFilterSelected(LibraryFilter.System(system)) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        SidebarIconButton(
            selected = false,
            icon = Icons.Filled.Add,
            contentDescription = stringResource(R.string.title_add_console),
            onClick = onAddConsole,
        )
    }
}

@Composable
private fun SidebarIconButton(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(LibrarySidebarButtonSize).controllerFocusGlow(CircleShape),
        shape = CircleShape,
        color = if (selected) Color(0xFF242424) else Color(0xFF161616),
        shadowElevation = 6.dp,
        tonalElevation = 0.dp,
        border = sidebarSelectionBorder(selected),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(26.dp),
                tint = if (selected) LibraryNeonGreen else Color.White,
            )
        }
    }
}

@Composable
private fun SidebarSystemLogoButton(
    meta: MetaSystemID,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(LibrarySidebarButtonSize).controllerFocusGlow(CircleShape),
        shape = CircleShape,
        color = Color(meta.color()),
        shadowElevation = 6.dp,
        tonalElevation = 0.dp,
        border = sidebarSelectionBorder(selected),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = meta.imageResId),
                contentDescription = stringResource(id = meta.titleResId),
                modifier = Modifier.fillMaxSize(0.84f),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun sidebarSelectionBorder(selected: Boolean) =
    BorderStroke(
        width = if (selected) 2.dp else 1.dp,
        color = if (selected) LibraryNeonGreen else Color(0xFF2E2E2E),
    )

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun LibraryGrid(
    modifier: Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    state: LibraryViewModel.UiState,
    games: LazyPagingItems<Game>,
    onGameClick: (Game) -> Unit,
    onContinueClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onSync: (Context) -> Unit,
    onZoomDensityChange: (Int) -> Unit,
    requestInitialFocus: Boolean = false,
    showZoomBar: Boolean = true,
) {
    val context = LocalContext.current
    val firstItemRequester = remember { FocusRequester() }
    val navigation = LocalControllerNavigation.current
    navigation?.contentFocusRequester = firstItemRequester
    val continueGame = state.continueGame
    val showContinue =
        state.filter is LibraryFilter.All &&
            state.searchQuery.isBlank() &&
            continueGame != null
    LaunchedEffect(requestInitialFocus, games.itemCount, showContinue) {
        if (requestInitialFocus && (games.itemCount > 0 || showContinue)) {
            runCatching { firstItemRequester.requestFocus() }
        }
    }
    val isRefreshing = games.loadState.refresh is LoadState.Loading
    val isEmpty = games.itemCount == 0 && !showContinue && !isRefreshing
    val showSyncEmpty =
        isEmpty &&
            state.searchQuery.isBlank() &&
            state.filter !is LibraryFilter.Favorites
    val infoStyle =
        when (state.zoomDensity) {
            0 -> GameCardInfoStyle.BELOW
            1 -> GameCardInfoStyle.OVERLAY
            else -> GameCardInfoStyle.MINIMAL
        }

    when {
        isRefreshing && games.itemCount == 0 -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        showSyncEmpty -> {
            LibrarySyncEmpty(
                modifier = modifier,
                scanning = state.operationInProgress,
                onSync = { onSync(context) },
            )
        }
        isEmpty -> {
            OmnidroidEmptyView(modifier = modifier)
        }
        else -> {
            Column(modifier = modifier.fillMaxSize()) {
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                ) {
                    val rows = libraryGridRows(state.zoomDensity, maxHeight)
                    LazyHorizontalGrid(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .focusGroup(),
                        rows = GridCells.Fixed(rows),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val featured = continueGame.takeIf { showContinue }
                        if (featured != null) {
                            item(key = "continue-${featured.id}") {
                                LibraryGameCardItem(
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    game = featured,
                                    modifier = Modifier.focusRequester(firstItemRequester),
                                    continueAction = true,
                                    infoStyle = infoStyle,
                                    onClick = { onContinueClick(featured) },
                                    onLongClick = { onGameLongClick(featured) },
                                )
                            }
                        }

                        items(
                            count = games.itemCount,
                            key = { index -> games.peek(index)?.id ?: index },
                        ) { index ->
                            val game = games[index] ?: return@items
                            LibraryGameCardItem(
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                game = game,
                                modifier =
                                    if (!showContinue && index == 0) {
                                        Modifier.focusRequester(firstItemRequester)
                                    } else {
                                        Modifier
                                    },
                                continueAction = false,
                                infoStyle = infoStyle,
                                onClick = { onGameClick(game) },
                                onLongClick = { onGameLongClick(game) },
                            )
                        }
                    }
                }
                if (showZoomBar) {
                    LibraryZoomBar(
                        density = state.zoomDensity,
                        onDensityChange = onZoomDensityChange,
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun LibraryGameCardItem(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    game: Game,
    modifier: Modifier = Modifier,
    continueAction: Boolean,
    infoStyle: GameCardInfoStyle,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val sizeModifier =
        Modifier
            .fillMaxHeight()
            .then(
                if (infoStyle == GameCardInfoStyle.BELOW) {
                    Modifier
                } else {
                    Modifier.aspectRatio(LibraryGameCardAspectRatio, matchHeightConstraintsFirst = true)
                },
            )
    val cornerAnim by animatedVisibilityScope.transition.animateDp(
        label = "coverCorner-${game.id}",
        transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) },
    ) { targetState ->
        if (targetState == EnterExitState.Visible) 4.dp else 16.dp
    }
    val coverSharedModifier =
        with(sharedTransitionScope) {
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
    OmnidroidGameCard(
        modifier = modifier.then(sizeModifier),
        coverModifier = coverSharedModifier,
        game = game,
        continueAction = continueAction,
        infoStyle = infoStyle,
        fillCard = true,
        cornerRadius = cornerAnim,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

@Composable
private fun LibraryZoomBar(
    density: Int,
    onDensityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoomOutEnabled = density < LibraryViewModel.MAX_ZOOM_DENSITY
    val zoomInEnabled = density > 0
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .controllerFocusGlow(CircleShape)
                    .clickable(enabled = zoomOutEnabled) {
                        onDensityChange(density + 1)
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ZoomOut,
                contentDescription = stringResource(R.string.library_zoom_out),
                modifier = Modifier.size(18.dp),
                tint = Color.White.copy(alpha = if (zoomOutEnabled) 1f else 0.35f),
            )
        }
        ZoomTrack(
            position = LibraryViewModel.MAX_ZOOM_DENSITY - density,
            onPositionChange = { onDensityChange(LibraryViewModel.MAX_ZOOM_DENSITY - it) },
            modifier =
                Modifier
                    .width(168.dp)
                    .height(24.dp)
                    .padding(horizontal = 8.dp)
                    .controllerFocusGlow()
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> {
                                if (zoomOutEnabled) onDensityChange(density + 1)
                                true
                            }
                            Key.DirectionRight -> {
                                if (zoomInEnabled) onDensityChange(density - 1)
                                true
                            }
                            else -> false
                        }
                    },
        )
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .controllerFocusGlow(CircleShape)
                    .clickable(enabled = zoomInEnabled) {
                        onDensityChange(density - 1)
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ZoomIn,
                contentDescription = stringResource(R.string.library_zoom_in),
                modifier = Modifier.size(18.dp),
                tint = Color.White.copy(alpha = if (zoomInEnabled) 1f else 0.35f),
            )
        }
    }
}

@Composable
private fun ZoomTrack(
    position: Int,
    onPositionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbColor = LibraryNeonGreen
    val trackColor = Color(0xFF8A8A8A)
    Box(
        modifier =
            modifier.pointerInput(Unit) {
                val snap = { x: Float ->
                    val pad = size.height / 2f
                    val usable = (size.width - 2f * pad).coerceAtLeast(1f)
                    val t = ((x - pad) / usable).coerceIn(0f, 1f)
                    onPositionChange((t * LibraryViewModel.MAX_ZOOM_DENSITY).roundToInt())
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    snap(down.position.x)
                    drag(down.id) { change ->
                        snap(change.position.x)
                        change.consume()
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pad = size.height / 2f
            val y = size.height / 2f
            val start = pad
            val end = size.width - pad
            drawLine(
                color = trackColor,
                start = Offset(start, y),
                end = Offset(end, y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            val cx = start + (end - start) * (position / LibraryViewModel.MAX_ZOOM_DENSITY.toFloat())
            drawCircle(
                color = thumbColor,
                radius = 7.dp.toPx(),
                center = Offset(cx, y),
            )
        }
    }
}

@Composable
private fun LibrarySyncEmpty(
    modifier: Modifier,
    scanning: Boolean,
    onSync: () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        FilledTonalButton(
            onClick = { if (!scanning) onSync() },
            modifier = Modifier.controllerFocusGlow(),
        ) {
            RescanIcon(
                spinning = scanning,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(
                    if (scanning) R.string.library_scanning else R.string.rescan,
                ),
            )
        }
    }
}
