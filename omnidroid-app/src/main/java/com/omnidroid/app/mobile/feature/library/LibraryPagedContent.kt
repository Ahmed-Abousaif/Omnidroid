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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

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
        PrefetchViewportCovers(items, state, lanes)
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
            // Continue can duplicate a normal game, but must not duplicate its shared-cover match.
            // Two differently sized matches in one screen can keep lookahead measuring forever.
            val modifier = if (index == 0) cellModifier.focusRequester(firstItemRequester) else cellModifier
            val onClick = { if (featured) onContinueClick(game) else onGameClick(game) }
            if (isList) {
                LibraryGameRow(
                    sharedTransitionScope, animatedVisibilityScope, game, modifier,
                    continueAction = featured, sharedCover = !featured,
                    onClick = onClick, onLongClick = { onGameLongClick(game) },
                )
            } else {
                LibraryGameCardItem(
                    sharedTransitionScope, animatedVisibilityScope, game, modifier,
                    continueAction = featured, infoStyle = infoStyle, fillCard = fillCard,
                    decodeWidth = decodeWidth,
                    sharedCover = !featured,
                    onClick = onClick, onLongClick = { onGameLongClick(game) },
                )
            }
        }
    }
}

@Composable
private fun PrefetchViewportCovers(items: LibraryPagedItems, state: LazyGridState, lanes: Int) {
    val context = LocalContext.current
    LaunchedEffect(items, state, lanes, context) {
        var previousFirst = 0
        snapshotFlow {
            val visible = state.layoutInfo.visibleItemsInfo
            Triple(visible.minOfOrNull { it.index } ?: 0, visible.maxOfOrNull { it.index } ?: -1, items.count)
        }.collectLatest { (first, last, count) ->
            val forward = first >= previousFirst
            previousFirst = first
            // Coalesce fling updates and cancel obsolete work instead of launching more jobs.
            delay(100)
            val range = libraryCoverPrefetchRange(first, last, count, lanes, forward)
            val candidates = if (forward) range.mapNotNull(items::peek) else range.reversed().mapNotNull(items::peek)
            LibraryCoverPrefetcher.prefetch(context, candidates)
        }
    }
}

internal fun libraryCoverPrefetchRange(
    first: Int,
    last: Int,
    count: Int,
    lanes: Int,
    forward: Boolean,
): IntRange {
    if (count <= 0 || last < first) return IntRange.EMPTY
    val ahead = (lanes * 2).coerceIn(4, 24)
    return if (forward) {
        (last + 1).coerceAtMost(count)..(last + ahead).coerceAtMost(count - 1)
    } else {
        (first - ahead).coerceAtLeast(0)..(first - 1).coerceAtMost(count - 1)
    }
}
