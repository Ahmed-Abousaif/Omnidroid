package com.omnidroid.app.shared.library

import android.content.Context
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.lib.library.db.entity.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Sequential, cancellable cover processing for the active viewport's small lookahead. */
object LibraryCoverPrefetcher {
    suspend fun prefetch(
        context: Context,
        games: List<Game>,
    ) = withContext(Dispatchers.IO) {
        if (games.isEmpty()) return@withContext
        val store = CoverUtils.coverCache(context)
        for (game in games) {
            ensureActive()
            val url = game.coverFrontUrl ?: continue
            if (game.customCoverPath == null && CoverUtils.isAllowedRemoteCoverUrl(url)) {
                try {
                    store.ensureGrid(game.id, url)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    // Prefetch is optional: a failed cache write must not take down the screen.
                    Timber.w(failure, "Unable to prefetch library cover for game %s", game.id)
                }
            }
        }
    }
}
