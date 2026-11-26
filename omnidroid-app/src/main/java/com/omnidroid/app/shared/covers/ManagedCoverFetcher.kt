package com.omnidroid.app.shared.covers

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.buffer
import okio.source
import java.io.IOException

data class ManagedCover(
    val gameId: Int,
    val url: String,
    val tier: CoverCacheStore.Tier,
)

/** Coil consumes the same prepared file as prefetch/worker/detail; it never starts a second GET. */
class ManagedCoverFetcher
    internal constructor(
        private val data: ManagedCover,
        private val options: Options,
        private val storeProvider: (Context) -> CoverCacheStore,
    ) : Fetcher {
        constructor(
            data: ManagedCover,
            options: Options,
        ) : this(data, options, CoverUtils::coverCache)

        override suspend fun fetch(): SourceResult {
            val file =
                CoverUtils
                    .ensureWithMirror(
                        storeProvider(options.context),
                        data.gameId,
                        data.url,
                        data.tier,
                    )?.second
                    ?: throw IOException("Artwork temporarily unavailable")
            return SourceResult(
                ImageSource(file.source().buffer(), options.context),
                "image/${file.extension}",
                DataSource.DISK,
            )
        }

        class Factory : Fetcher.Factory<ManagedCover> {
            override fun create(
                data: ManagedCover,
                options: Options,
                imageLoader: ImageLoader,
            ): Fetcher = ManagedCoverFetcher(data, options)
        }
    }
