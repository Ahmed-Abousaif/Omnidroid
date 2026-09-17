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
            customCoverCacheKey(game)?.let { key ->
                memoryCacheKey(key)
                diskCacheKey(key)
            }
        }
    }

    fun coverData(game: Game): Any? =
        resolveCustomCoverFile(game) ?: RawgCoverStore.get(game.id) ?: game.coverFrontUrl

    fun coverData(
        game: Game,
        preferredCoverUrl: String?,
    ): Any? =
        resolveCustomCoverFile(game)
            ?: preferredCoverUrl?.takeIf { it.isNotBlank() }
            ?: RawgCoverStore.get(game.id)
            ?: game.coverFrontUrl

    fun coverRequest(
        context: Context,
        game: Game,
        preferredCoverUrl: String? = null,
    ): ImageRequest {
        return ImageRequest.Builder(context)
            .data(coverData(game, preferredCoverUrl))
            .apply {
                customCoverCacheKey(game)?.let { key ->
                    memoryCacheKey(key)
                    diskCacheKey(key)
                }
            }
            .build()
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

    private fun customCoverCacheKey(game: Game): String? {
        val file = resolveCustomCoverFile(game) ?: return null
        return "${file.absolutePath}:${file.lastModified()}"
    }
}
