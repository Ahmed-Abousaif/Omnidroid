package com.omnidroid.app.shared.covers

import android.content.Context
import android.widget.ImageView
import coil.ImageLoader
import coil.disk.DiskCache
import coil.imageLoader
import coil.load
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.omnidroid.common.drawable.TextDrawable
import com.omnidroid.common.graphics.ColorUtils
import com.omnidroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.io.File

object CoverUtils {
    fun loadCover(
        game: Game,
        imageView: ImageView?,
    ) {
        if (imageView == null) return

        imageView.load(coverData(game), imageView.context.imageLoader) {
            val fallbackDrawable = getFallbackDrawable(game)
            fallback(fallbackDrawable)
            error(fallbackDrawable)
            val key = coverCacheKey(game)
            memoryCacheKey(key)
            diskCacheKey(key)
        }
    }

    private val ALLOWED_COVER_HOSTS = setOf(
        "thumbnails.libretro.com",
        "media.rawg.io",
        "rawg.io",
        "images.igdb.com",
    )

    fun isAllowedRemoteCoverUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        return ALLOWED_COVER_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    fun coverData(game: Game): Any? =
        resolveCustomCoverFile(game)
            ?: RawgCoverStore.get(game.id)?.takeIf(::isAllowedRemoteCoverUrl)
            ?: game.coverFrontUrl?.takeIf(::isAllowedRemoteCoverUrl)

    fun coverData(
        game: Game,
        preferredCoverUrl: String?,
    ): Any? =
        resolveCustomCoverFile(game)
            ?: preferredCoverUrl?.takeIf(::isAllowedRemoteCoverUrl)
            ?: RawgCoverStore.get(game.id)?.takeIf(::isAllowedRemoteCoverUrl)
            ?: game.coverFrontUrl?.takeIf(::isAllowedRemoteCoverUrl)

    fun coverCacheKey(
        game: Game,
        preferredCoverUrl: String? = null,
    ): String {
        val file = resolveCustomCoverFile(game)
        if (file != null) {
            return "custom:${game.id}:${file.absolutePath}:${file.lastModified()}"
        }
        val data = coverData(game, preferredCoverUrl)?.toString() ?: "game:${game.id}"
        return "cover:${game.id}:$data"
    }

    fun coverRequest(
        context: Context,
        game: Game,
        preferredCoverUrl: String? = null,
    ): ImageRequest {
        val key = coverCacheKey(game, preferredCoverUrl)
        return ImageRequest.Builder(context)
            .data(coverData(game, preferredCoverUrl))
            .memoryCacheKey(key)
            .diskCacheKey(key)
            .build()
    }

    fun getCachedCoverAspectRatio(
        context: Context,
        game: Game,
        preferredCoverUrl: String? = null,
    ): Float? {
        val file = resolveCustomCoverFile(game)
        if (file != null && file.isFile && file.length() > 0L) {
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
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

    fun hasCustomCover(game: Game): Boolean = resolveCustomCoverFile(game) != null

    fun resolveCustomCoverFile(game: Game): File? {
        return game.customCoverPath
            ?.let(CustomCoverManager::fileForStoredPath)
            ?.takeIf { it.isFile && it.length() > 0L }
    }

    fun buildImageLoader(applicationContext: Context): ImageLoader {
        return ImageLoader.Builder(applicationContext)
            .diskCache(
                DiskCache.Builder()
                    .directory(applicationContext.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.20)
                    .build(),
            )
            .memoryCache {
                MemoryCache.Builder(applicationContext)
                    .maxSizePercent(0.20)
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor(SecureCoverInterceptor)
                    .addNetworkInterceptor(ThrottleFailedThumbnailsInterceptor)
                    .build()
            }
            .crossfade(true)
            .interceptorDispatcher(Dispatchers.IO)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false)
            .build()
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

        return sanitizedName.asSequence()
            .filter { it.isDigit() or it.isUpperCase() or (it == '&') }
            .take(3)
            .joinToString("")
            .ifBlank { game.title.first().toString() }
            .capitalize()
    }

    private fun computeColor(game: Game): Int {
        return ColorUtils.randomColor(game.title)
    }

    fun customCoverCacheKey(game: Game): String? {
        val file = resolveCustomCoverFile(game) ?: return null
        return "${file.absolutePath}:${file.lastModified()}"
    }
}
