package com.omnidroid.app.shared.covers

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.buffer
import okio.source
import java.io.IOException

data class ManagedCover(val gameId: Int, val url: String, val tier: CoverCacheStore.Tier)

/** Coil consumes the same prepared file as prefetch/worker/detail; it never starts a second GET. */
class ManagedCoverFetcher(private val data: ManagedCover, private val options: Options) : Fetcher {
    override suspend fun fetch(): SourceResult {
        val store = CoverUtils.coverCache(options.context)
        val file = when (data.tier) {
            CoverCacheStore.Tier.GRID -> store.ensureGrid(data.gameId, data.url)
            CoverCacheStore.Tier.DETAIL -> store.ensureDetail(data.gameId, data.url)
        } ?: throw IOException("Artwork temporarily unavailable")
        return SourceResult(ImageSource(file.source().buffer(), options.context), "image/${file.extension}", DataSource.DISK)
    }

    class Factory : Fetcher.Factory<ManagedCover> {
        override fun create(data: ManagedCover, options: Options, imageLoader: ImageLoader): Fetcher = ManagedCoverFetcher(data, options)
    }
}
