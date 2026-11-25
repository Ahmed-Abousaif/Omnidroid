package com.omnidroid.app.shared.library

import android.content.Context
import coil.imageLoader
import coil.size.Size
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.lib.library.db.entity.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.io.File

/** Cancellable cover preparation for the cells just past the viewport, nearest first. */
object LibraryCoverPrefetcher {
    private const val PARALLELISM = 4

    /**
     * Downloads missing grid covers and, when [decodeSize] is given, decodes each cover into Coil's
     * memory cache under the same key and size the grid cell requests. A cell that scrolls in then
     * draws its cover straight from memory instead of showing a blank card while it decodes.
     * [stillWanted] lets a fast scroll skip covers that have already gone past.
     */
    suspend fun prefetch(
        context: Context,
        games: List<Game>,
        decodeSize: Size? = null,
        stillWanted: (Game) -> Boolean = { true },
    ) = coroutineScope {
        if (games.isEmpty()) return@coroutineScope
        val permits = Semaphore(PARALLELISM)
        for (game in games) {
            ensureActive()
            launch(Dispatchers.IO) {
                permits.withPermit {
                    if (stillWanted(game)) prepare(context, game, decodeSize)
                }
            }
        }
    }

    private suspend fun prepare(
        context: Context,
        game: Game,
        decodeSize: Size?,
    ) {
        try {
            val url = game.coverFrontUrl
            if (url != null && game.customCoverPath == null && CoverUtils.isAllowedRemoteCoverUrl(url)) {
                CoverUtils.coverCache(context).ensureGrid(game.id, url)
            }
            val settings =
                com.omnidroid.app.shared.metadata
                    .MetadataSettings(context.applicationContext)
            val network = context.getSystemService(android.net.ConnectivityManager::class.java)
            val capabilities = network?.getNetworkCapabilities(network.activeNetwork)
            val allowedNetwork =
                capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                    (
                        !settings.wifiOnly ||
                            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    )
            if (settings.enabled &&
                allowedNetwork &&
                game.customCoverPath == null &&
                (url == null || CoverUtils.coverCache(context).failure(url)?.state == "missing")
            ) {
                com.omnidroid.app.shared.metadata.MetadataRepository
                    .get(context)
                    .enrich(listOf(game))
            }
            // Only warm covers already on disk; remote fallbacks are left to the cell.
            if (decodeSize != null && CoverUtils.coverData(game) is File) {
                context.imageLoader.execute(CoverUtils.coverRequest(context, game, size = decodeSize))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Prefetch is optional: a failed cache write must not take down the screen.
            Timber.w(failure, "Unable to prefetch library cover for game %s", game.id)
        }
    }
}
