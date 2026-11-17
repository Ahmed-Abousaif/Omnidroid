package com.omnidroid.app.mobile.feature.library

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.omnidroid.app.mobile.shared.compose.ui.GameCardInfoStyle
import com.omnidroid.app.mobile.shared.compose.ui.LibraryCardCaptionHeight
import com.omnidroid.app.mobile.shared.compose.ui.LibraryGameCardAspectRatio
import com.omnidroid.app.mobile.shared.compose.ui.LocalAdaptiveLayout
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.shared.library.LibraryCoverPrefetcher
import com.omnidroid.lib.library.db.entity.Game
import coil.size.Size
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicReference

@Stable
internal class LibraryPagedScrollState(val list: LazyGridState, val grid: LazyGridState)

@Composable
internal fun rememberLibraryPagedScrollState(): LibraryPagedScrollState {
    val list = rememberLazyGridState()
    val grid = rememberLazyGridState()
    return remember(list, grid) { LibraryPagedScrollState(list, grid) }
}

/** List is a one-column grid. All paged layouts share item identity, sizing and loading. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun LibraryPagedContent(
    games: LazyPagingItems<Game>,
    featured: Game?,
    layout: LibraryLayout,
    zoomDensity: Int,
    portrait: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    firstItemRequester: FocusRequester,
    onGameClick: (Game) -> Unit,
    onContinueClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: LibraryPagedScrollState = rememberLibraryPagedScrollState(),
) {
    val items = remember(games, featured) { LibraryPagedItems(games, featured) }
    // Kept independently when changing modes; reset by the parent's dataset key on search/filter/sort.
    val isList = layout == LibraryLayout.LIST
    val state = if (isList) scrollState.list else scrollState.grid
    val adaptive = LocalAdaptiveLayout.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val vertical = isList || portrait
        val lanes = when {
            isList -> 1
            portrait -> libraryPortraitColumns(zoomDensity, maxWidth, adaptive.isSquare)
            else -> libraryGridRows(zoomDensity, maxHeight, adaptive.isSquare)
        }
        val spacing = if (isList) 8.dp else adaptive.gridSpacing
        val horizontalPadding = when {
            isList -> 8.dp
            vertical -> spacing
            else -> spacing + 4.dp
        }
        val verticalPadding = if (isList) 12.dp else spacing
        val infoStyle = when {
            zoomDensity >= 2 -> GameCardInfoStyle.MINIMAL
            portrait || zoomDensity == 1 -> GameCardInfoStyle.OVERLAY
            else -> GameCardInfoStyle.BELOW
        }
        val cellHeight = ((maxHeight - verticalPadding * 2 - spacing * (lanes - 1)) / lanes)
            .coerceAtLeast(1.dp)
        val coverWidth = if (vertical) {
            ((maxWidth - horizontalPadding * 2 - spacing * (lanes - 1)) / lanes).coerceAtLeast(1.dp)
        } else {
            val coverHeight = if (infoStyle == GameCardInfoStyle.BELOW) {
                (cellHeight - LibraryCardCaptionHeight).coerceAtLeast(48.dp)
            } else cellHeight
            coverHeight * LibraryGameCardAspectRatio
        }
        val cellModifier = when {
            isList -> Modifier.fillMaxWidth().height(adaptive.listThumbHeight + 8.dp)
            vertical -> Modifier.fillMaxWidth().aspectRatio(LibraryGameCardAspectRatio)
            else -> Modifier.width(coverWidth).fillMaxHeight()
        }
        val content: LazyGridScope.() -> Unit = {
            libraryCells(
                items = items,
                isList = isList,
                cellModifier = cellModifier,
                infoStyle = infoStyle,
                fillCard = !vertical,
                decodeWidth = coverWidth,
                firstItemRequester = firstItemRequester,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onGameClick = onGameClick,
                onContinueClick = onContinueClick,
                onGameLongClick = onGameLongClick,
            )
        }
        val padding = PaddingValues(horizontal = horizontalPadding, vertical = verticalPadding)
        val arrangement = Arrangement.spacedBy(spacing)
        if (vertical) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(lanes),
                state = state,
                modifier = Modifier.fillMaxSize().focusGroup(),
                contentPadding = padding,
                horizontalArrangement = arrangement,
                verticalArrangement = arrangement,
                content = content,
            )
        } else {
            LazyHorizontalGrid(
                rows = GridCells.Fixed(lanes),
                state = state,
                modifier = Modifier.fillMaxSize().focusGroup(),
                contentPadding = padding,
                horizontalArrangement = arrangement,
                verticalArrangement = arrangement,
                content = content,
            )
        }
        // Grid cells request covers at this size (OmnidroidGameImage); list rows use small thumbs.
        val decodePx = with(LocalDensity.current) { coverWidth.roundToPx() }
        val decodeSize =
            remember(isList, decodePx) {
                if (isList) null else Size(decodePx, (decodePx / LibraryGameCardAspectRatio).toInt())
            }
        PrefetchViewportCovers(items, state, lanes, decodeSize)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
private fun LazyGridScope.libraryCells(
    items: LibraryPagedItems,
    isList: Boolean,
    cellModifier: Modifier,
    infoStyle: GameCardInfoStyle,
    fillCard: Boolean,
    decodeWidth: Dp,
    firstItemRequester: FocusRequester,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onGameClick: (Game) -> Unit,
    onContinueClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
) {
    items(count = items.count, key = items.key, contentType = items.contentType) { index ->
        val game = items[index]
        if (game == null) {
            // Preserve loaded-cell geometry. Empty slots otherwise collapse and cascade loads.
            Box(
                cellModifier
                    .background(chromeColors.listRow, RoundedCornerShape(if (isList) 10.dp else 4.dp))
                    .clearAndSetSemantics {},
            )
        } else {
            val featured = items.isFeatured(index)
            // Continue and the ordinary copy use separate shared-cover identities.
            val modifier = if (index == 0) cellModifier.focusRequester(firstItemRequester) else cellModifier
            val onClick = { if (featured) onContinueClick(game) else onGameClick(game) }
            if (isList) {
                LibraryGameRow(
                    sharedTransitionScope, animatedVisibilityScope, game, modifier,
                    continueAction = featured,
                    onClick = onClick, onLongClick = { onGameLongClick(game) },
                )
            } else {
                LibraryGameCardItem(
                    sharedTransitionScope, animatedVisibilityScope, game, modifier,
                    continueAction = featured, infoStyle = infoStyle, fillCard = fillCard,
                    decodeWidth = decodeWidth,
                    onClick = onClick, onLongClick = { onGameLongClick(game) },
                )
            }
        }
    }
}

@Composable
private fun PrefetchViewportCovers(items: LibraryPagedItems, state: LazyGridState, lanes: Int, decodeSize: Size?) {
    val context = LocalContext.current
    LaunchedEffect(items, state, lanes, context, decodeSize) {
        var previousFirst = 0
        val window = AtomicReference(IntRange.EMPTY)
        val margin = libraryCoverPrefetchAhead(lanes)
        snapshotFlow {
            val visible = state.layoutInfo.visibleItemsInfo
            Triple(visible.minOfOrNull { it.index } ?: 0, visible.maxOfOrNull { it.index } ?: -1, items.count)
        }
            .onEach { (first, last, _) -> window.set(first..last) }
            // Finish the current batch, then jump to the newest viewport. collectLatest restarted on
            // every fling frame, so nothing was prepared until the scroll stopped.
            .conflate()
            .collect { (first, last, count) ->
                val forward = first >= previousFirst
                previousFirst = first
                val range = libraryCoverPrefetchRange(first, last, count, lanes, forward)
                val ordered = if (forward) range else range.reversed()
                val indexById = HashMap<Int, Int>()
                val candidates = ordered.mapNotNull { index -> items.peek(index)?.also { indexById[it.id] = index } }
                LibraryCoverPrefetcher.prefetch(context, candidates, decodeSize) { game ->
                    val index = indexById[game.id] ?: return@prefetch false
                    val now = window.get()
                    index in (now.first - margin)..(now.last + margin)
                }
            }
    }
}

/** Cells prepared beyond the viewport: about two screens of rows, within the memory cache. */
internal fun libraryCoverPrefetchAhead(lanes: Int): Int = (lanes * 6).coerceIn(12, 36)

internal fun libraryCoverPrefetchRange(
    first: Int,
    last: Int,
    count: Int,
    lanes: Int,
    forward: Boolean,
): IntRange {
    if (count <= 0 || last < first) return IntRange.EMPTY
    val ahead = libraryCoverPrefetchAhead(lanes)
    return if (forward) {
        (last + 1).coerceAtMost(count)..(last + ahead).coerceAtMost(count - 1)
    } else {
        (first - ahead).coerceAtLeast(0)..(first - 1).coerceAtMost(count - 1)
    }
}
