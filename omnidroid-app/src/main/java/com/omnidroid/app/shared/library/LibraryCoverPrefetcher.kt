package com.omnidroid.app.shared.library

import android.content.Context
import androidx.paging.compose.LazyPagingItems
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.lib.library.db.entity.Game
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Prefetch processed grid covers for the next ~2 rows ahead of the visible window. */
object LibraryCoverPrefetcher {
    fun prefetchAhead(
        context: Context,
        scope: CoroutineScope,
        games: LazyPagingItems<Game>,
        firstVisible: Int,
        visibleCount: Int,
        columnsOrRows: Int,
    ) {
        val ahead = (columnsOrRows * 2).coerceAtLeast(4)
        val start = (firstVisible + visibleCount).coerceAtLeast(0)
        val end = (start + ahead).coerceAtMost(games.itemCount)
        if (start >= end) return
        val store = CoverUtils.coverCache(context)
        scope.launch(Dispatchers.IO) {
            for (index in start until end) {
                val game = games.peek(index) ?: continue
                val url = game.coverFrontUrl ?: continue
                if (CoverUtils.isAllowedRemoteCoverUrl(url)) {
                    store.ensureGrid(game.id, url)
                }
            }
        }
    }
}
