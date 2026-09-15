package com.omnidroid.app.mobile.feature.games

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.paging.compose.collectAsLazyPagingItems
import com.omnidroid.app.mobile.shared.compose.ui.OmnidroidEmptyView
import com.omnidroid.app.mobile.shared.compose.ui.OmnidroidGameListRow
import com.omnidroid.lib.library.db.entity.Game

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GamesScreen(
    modifier: Modifier = Modifier,
    viewModel: GamesViewModel,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onGameFavoriteToggle: (Game, Boolean) -> Unit,
) {
    val games = viewModel.games.collectAsLazyPagingItems()

    if (games.itemCount == 0) {
        OmnidroidEmptyView()
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(games.itemCount, key = { games[it]?.id ?: it }) { index ->
            val game = games[index] ?: return@items

            OmnidroidGameListRow(
                modifier = Modifier.animateItem(),
                game = game,
                onClick = { onGameClick(game) },
                onLongClick = { onGameLongClick(game) },
                onFavoriteToggle = { isFavorite -> onGameFavoriteToggle(game, isFavorite) },
            )
        }
    }
}
