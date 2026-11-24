package com.omnidroid.app.shared.covers

import android.content.Context
import android.graphics.BitmapFactory
import coil.ImageLoader
import coil.disk.DiskCache
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.omnidroid.common.drawable.TextDrawable
import com.omnidroid.common.graphics.ColorUtils
import com.omnidroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale

object CoverUtils {
    @Volatile
    private var coverCacheStore: CoverCacheStore? = null

    fun coverCache(context: Context): CoverCacheStore {
        coverCacheStore?.let { return it }
        return synchronized(this) {
            coverCacheStore ?: CoverCacheStore(context.applicationContext).also { coverCacheStore = it }
        }
    }

    private val ALLOWED_COVER_HOSTS =
        setOf(
            "thumbnails.libretro.com",
            "images.igdb.com",
        )

    fun isAllowedRemoteCoverUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        if (host == "raw.githubusercontent.com") {
            val parts = uri.pathSegments
            return scheme == "https" &&
                uri.query == null &&
                parts.size in 5..6 &&
                parts[0] == "libretro-thumbnails" &&
                parts[1].matches(Regex("[A-Za-z0-9_-]+")) &&
                parts[2] == "master" &&
                parts[3] in setOf("Named_Boxarts", "Named_Titles", "Named_Snaps") &&
                parts.none { it == "." || it == ".." } &&
                parts.last().endsWith(".png", ignoreCase = true)
        }
        return ALLOWED_COVER_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    fun coverData(game: Game): Any? =
        resolvePreferredLocalCover(game)
            ?: resolveProcessedCover(game)
            ?: game.coverFrontUrl?.takeIf(::isAllowedRemoteCoverUrl)
            ?: resolveCustomCoverFile(game)
            ?: resolveEmbeddedCover(game)

    fun coverData(
        game: Game,
        preferredCoverUrl: String?,
    ): Any? =
        resolvePreferredLocalCover(game)
            ?: resolveProcessedCover(game, preferredCoverUrl)
            ?: preferredCoverUrl?.takeIf(::isAllowedRemoteCoverUrl)
            ?: game.coverFrontUrl?.takeIf(::isAllowedRemoteCoverUrl)
            ?: resolveCustomCoverFile(game)
            ?: resolveEmbeddedCover(game)

    private fun resolveProcessedCover(
        game: Game,
        preferredCoverUrl: String? = null,
    ): File? {
        val url = preferredCoverUrl ?: game.coverFrontUrl ?: return null
        val store = coverCacheStore ?: return null
        return store.preferredCoverFile(game.id, url, CoverCacheStore.Tier.GRID)
    }

    fun coverCacheKey(
        game: Game,
        preferredCoverUrl: String? = null,
    ): String {
        val file = resolvePreferredLocalCover(game)
        if (file != null) {
            return "custom:${game.id}:${file.absolutePath}:${file.lastModified()}"
        }
        val processed = resolveProcessedCover(game, preferredCoverUrl)
        if (processed != null) {
            return "processed:${game.id}:${processed.absolutePath}:${processed.lastModified()}"
        }
        val data = coverData(game, preferredCoverUrl)?.toString() ?: "game:${game.id}"
        return "cover:${game.id}:$data"
    }

    /**
     * [tier] DETAIL prefers the larger cached cover (downloaded by game details) and falls back to
     * the grid one until it exists; custom covers are always used as-is.
     */
    fun coverRequest(
        context: Context,
        game: Game,
        preferredCoverUrl: String? = null,
        size: Size = Size.ORIGINAL,
        tier: CoverCacheStore.Tier = CoverCacheStore.Tier.GRID,
    ): ImageRequest {
        val cache = coverCache(context)
        val url = preferredCoverUrl ?: game.coverFrontUrl
        val custom = resolvePreferredLocalCover(game)
        val detail =
            if (custom == null && tier == CoverCacheStore.Tier.DETAIL) {
                cache.preferredCoverFile(game.id, url, CoverCacheStore.Tier.DETAIL)
            } else {
                null
            }
        val processed = custom ?: detail ?: cache.preferredCoverFile(game.id, url, CoverCacheStore.Tier.GRID)
        val data =
            processed ?: url?.takeIf(::isAllowedRemoteCoverUrl)?.let { ManagedCover(game.id, it, tier) }
                ?: resolveCustomCoverFile(game) ?: resolveEmbeddedCover(game)
        val key =
            if (detail != null) {
                "processed-detail:${game.id}:${detail.absolutePath}:${detail.lastModified()}"
            } else {
                coverCacheKey(game, preferredCoverUrl)
            }
        return ImageRequest
            .Builder(context)
            .data(data)
            .memoryCacheKey(key)
            .diskCacheKey(key)
            .size(size)
            .precision(Precision.INEXACT)
            .build()
    }

    fun getCachedCoverAspectRatio(
        context: Context,
        game: Game,
        preferredCoverUrl: String? = null,
    ): Float? {
        val file = resolvePreferredLocalCover(game)
        if (file != null && file.isFile && file.length() > 0L) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                return options.outWidth.toFloat() / options.outHeight.toFloat()
            }
        }
        val key = coverCacheKey(game, preferredCoverUrl)
        val imageLoader = coil.Coil.imageLoader(context)
        val cachedBitmap = imageLoader.memoryCache?.get(MemoryCache.Key(key))?.bitmap
        if (cachedBitmap != null && cachedBitmap.width > 0 && cachedBitmap.height > 0) {
            return cachedBitmap.width.toFloat() / cachedBitmap.height.toFloat()
        }
        val data = coverData(game, preferredCoverUrl)?.toString()
        if (!data.isNullOrBlank()) {
            val rawBitmap = imageLoader.memoryCache?.get(MemoryCache.Key(data))?.bitmap
            if (rawBitmap != null && rawBitmap.width > 0 && rawBitmap.height > 0) {
                return rawBitmap.width.toFloat() / rawBitmap.height.toFloat()
            }
        }
        return null
    }

    fun hasCustomCover(game: Game): Boolean =
        game.customCoverOrigin != "embedded" && resolveCustomCoverFile(game) != null

    private fun resolvePreferredLocalCover(game: Game): File? =
        resolveCustomCoverFile(game)?.takeIf {
            game.customCoverOrigin != "embedded" ||
                !isAllowedRemoteCoverUrl(game.coverFrontUrl)
        }

    private fun resolveEmbeddedCover(game: Game): File? =
        game.coverFrontUrl
            ?.takeIf { it.startsWith("file:") }
            ?.let { runCatching { File(java.net.URI(it)) }.getOrNull() }
            ?.takeIf { it.isFile && it.length() > 0 }

    fun resolveCustomCoverFile(game: Game): File? =
        game.customCoverPath
            ?.let(CustomCoverManager::fileForStoredPath)
            ?.takeIf { it.isFile && it.length() > 0L }

    fun buildImageLoader(applicationContext: Context): ImageLoader {
        coverCache(applicationContext)
        return ImageLoader
            .Builder(applicationContext)
            .diskCache(
                DiskCache
                    .Builder()
                    .directory(applicationContext.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.10)
                    .build(),
            ).memoryCache {
                // Coil's default share; room for the visible grid plus the prefetched rows ahead.
                MemoryCache
                    .Builder(applicationContext)
                    .maxSizePercent(0.25)
                    .build()
            }.okHttpClient {
                OkHttpClient
                    .Builder()
                    .addInterceptor(SecureCoverInterceptor)
                    .addNetworkInterceptor(ThrottleFailedThumbnailsInterceptor)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
            }.components { add(ManagedCoverFetcher.Factory()) }
            .crossfade(true)
            .interceptorDispatcher(Dispatchers.IO)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false)
            .build()
    }

    fun trimMemory(context: Context) {
        runCatching {
            coil.Coil
                .imageLoader(context)
                .memoryCache
                ?.clear()
        }
    }

    fun getFallbackDrawable(game: Game) = TextDrawable(computeTitle(game), computeColor(game))

    fun getFallbackRemoteUrl(game: Game): String {
        val color = Integer.toHexString(computeColor(game)).substring(2)
        val title = computeTitle(game)
        return "https://fakeimg.pl/512x512/$color/fff/?font=bebas&text=$title"
    }

    private fun computeTitle(game: Game): String {
        val sanitizedName =
            game.title
                .replace(Regex("\\(.*\\)"), "")

        return sanitizedName
            .asSequence()
            .filter { it.isDigit() or it.isUpperCase() or (it == '&') }
            .take(3)
            .joinToString("")
            .ifBlank { game.title.first().toString() }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    private fun computeColor(game: Game): Int = ColorUtils.randomColor(game.title)
}
