package com.omnidroid.app.shared.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * On-disk processed cover cache: grid WebP (JPEG fallback), 300 MB LRU.
 * Detail tier is generated lazily when requested.
 */
class CoverCacheStore(
    private val context: Context,
    private val httpClient: OkHttpClient = defaultClient(),
) {
    enum class Tier { GRID, DETAIL }

    private val root = File(context.filesDir, COVER_CACHE_DIR).also { it.mkdirs() }
    private val metaMutex = Mutex()
    private val negativeCache = LruCache<String, Long>(512)
    private val prefs =
        context.getSharedPreferences(TELEMETRY_PREFS, Context.MODE_PRIVATE)

    @Volatile
    var lastEviction: EvictionStats? = null
        private set

    data class EvictionStats(
        val evictedFiles: Int,
        val evictedBytes: Long,
        val bytesAfter: Long,
        val atMs: Long = System.currentTimeMillis(),
    )

    fun evictionTotals(): Pair<Int, Long> {
        val files = prefs.getInt(KEY_EVICTED_FILES, 0)
        val bytes = prefs.getLong(KEY_EVICTED_BYTES, 0L)
        return files to bytes
    }

    fun gridFile(
        gameId: Int,
        sourceUrl: String,
    ): File? = existingFile(gameId, sourceUrl, Tier.GRID)

    fun detailFile(
        gameId: Int,
        sourceUrl: String,
    ): File? = existingFile(gameId, sourceUrl, Tier.DETAIL)

    fun preferredCoverFile(
        gameId: Int,
        sourceUrl: String?,
        tier: Tier = Tier.GRID,
    ): File? {
        if (sourceUrl.isNullOrBlank()) return null
        return existingFile(gameId, sourceUrl, tier)
    }

    suspend fun ensureGrid(
        gameId: Int,
        sourceUrl: String,
    ): File? = ensure(gameId, sourceUrl, Tier.GRID)

    suspend fun ensureDetail(
        gameId: Int,
        sourceUrl: String,
    ): File? {
        ensure(gameId, sourceUrl, Tier.GRID)
        return ensure(gameId, sourceUrl, Tier.DETAIL)
    }

    private fun existingFile(
        gameId: Int,
        sourceUrl: String,
        tier: Tier,
    ): File? {
        val base = cacheBase(gameId, sourceUrl, tier)
        val webp = File("$base.webp")
        if (webp.isFile && webp.length() > 0L) {
            touch(webp)
            return webp
        }
        val jpeg = File("$base.jpg")
        if (jpeg.isFile && jpeg.length() > 0L) {
            touch(jpeg)
            return jpeg
        }
        return null
    }

    private suspend fun ensure(
        gameId: Int,
        sourceUrl: String,
        tier: Tier,
    ): File? =
        withContext(Dispatchers.IO) {
            existingFile(gameId, sourceUrl, tier)?.let { return@withContext it }
            if (!CoverUtils.isAllowedRemoteCoverUrl(sourceUrl)) return@withContext null
            val negKey = "$gameId|$sourceUrl"
            val negUntil = negativeCache.get(negKey)
            if (negUntil != null && negUntil > System.currentTimeMillis()) {
                return@withContext null
            }

            val bytes =
                runCatching { download(sourceUrl) }.getOrElse {
                    Timber.w(it, "Cover download failed for $sourceUrl")
                    negativeCache.put(negKey, System.currentTimeMillis() + NEGATIVE_TTL_MS)
                    return@withContext null
                }
            if (bytes == null) {
                negativeCache.put(negKey, System.currentTimeMillis() + NEGATIVE_TTL_MS)
                return@withContext null
            }

            val maxEdge = if (tier == Tier.GRID) GRID_MAX_EDGE else DETAIL_MAX_EDGE
            val bitmap = decodeScaled(bytes, maxEdge) ?: run {
                negativeCache.put(negKey, System.currentTimeMillis() + NEGATIVE_TTL_MS)
                return@withContext null
            }
            try {
                val base = cacheBase(gameId, sourceUrl, tier)
                File(base).parentFile?.mkdirs()
                val encoded = encode(bitmap, base)
                metaMutex.withLock { evictIfNeeded() }
                encoded
            } finally {
                bitmap.recycle()
            }
        }

    private fun download(url: String): ByteArray? {
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            val type = body.contentType()?.toString().orEmpty()
            if (type.isNotBlank() && !type.startsWith("image/")) return null
            val bytes = body.bytes()
            if (bytes.isEmpty() || bytes.size > MAX_DOWNLOAD_BYTES) return null
            return bytes
        }
    }

    private fun decodeScaled(
        bytes: ByteArray,
        maxEdge: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample =
            max(1, max(bounds.outWidth, bounds.outHeight) / maxEdge).let { s ->
                Integer.highestOneBit(s).coerceAtLeast(1)
            }
        val opts =
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val longest = max(bitmap.width, bitmap.height)
        if (longest > maxEdge) {
            val scale = maxEdge.toFloat() / longest
            val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }
        return bitmap
    }

    private fun encode(
        bitmap: Bitmap,
        basePathWithoutExt: String,
    ): File? {
        val tmp = File("$basePathWithoutExt.tmp")
        val started = System.nanoTime()
        val webpOk =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    FileOutputStream(tmp).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, out)
                    }
                }.getOrDefault(false)
            } else {
                @Suppress("DEPRECATION")
                runCatching {
                    FileOutputStream(tmp).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.WEBP, WEBP_QUALITY, out)
                    }
                }.getOrDefault(false)
            }
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        if (webpOk && tmp.length() > 0L && elapsedMs <= WEBP_SLOW_MS) {
            val dest = File("$basePathWithoutExt.webp")
            if (tmp.renameTo(dest) || (tmp.copyTo(dest, overwrite = true).also { tmp.delete() }).exists()) {
                return dest
            }
        }
        tmp.delete()
        val jpegTmp = File("$basePathWithoutExt.jpg.tmp")
        val jpegOk =
            runCatching {
                FileOutputStream(jpegTmp).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
            }.getOrDefault(false)
        if (!jpegOk || jpegTmp.length() <= 0L) {
            jpegTmp.delete()
            return null
        }
        val dest = File("$basePathWithoutExt.jpg")
        return if (jpegTmp.renameTo(dest) ||
            (jpegTmp.copyTo(dest, overwrite = true).also { jpegTmp.delete() }).exists()
        ) {
            dest
        } else {
            null
        }
    }

    private fun cacheBase(
        gameId: Int,
        sourceUrl: String,
        tier: Tier,
    ): String {
        val hash = sha1(sourceUrl)
        val tierName = if (tier == Tier.GRID) "grid" else "detail"
        return File(root, "$gameId/${hash}_v${SETTINGS_VERSION}_$tierName").absolutePath
    }

    private fun touch(file: File) {
        file.setLastModified(System.currentTimeMillis())
    }

    private fun evictIfNeeded() {
        val files =
            root.walkTopDown()
                .filter { it.isFile && (it.extension == "webp" || it.extension == "jpg") }
                .sortedBy { it.lastModified() }
                .toList()
        var total = files.sumOf { it.length() }
        if (total <= MAX_CACHE_BYTES) return
        var index = 0
        var evictedFiles = 0
        var evictedBytes = 0L
        while (total > MAX_CACHE_BYTES && index < files.size) {
            val file = files[index++]
            val size = file.length()
            if (file.delete()) {
                total -= size
                evictedFiles++
                evictedBytes += size
            }
        }
        if (evictedFiles > 0) {
            val stats = EvictionStats(evictedFiles, evictedBytes, total)
            lastEviction = stats
            prefs.edit()
                .putInt(KEY_EVICTED_FILES, prefs.getInt(KEY_EVICTED_FILES, 0) + evictedFiles)
                .putLong(KEY_EVICTED_BYTES, prefs.getLong(KEY_EVICTED_BYTES, 0L) + evictedBytes)
                .apply()
            Timber.i(
                "Cover cache eviction: removed %d files (%d bytes), cache now %d bytes",
                evictedFiles,
                evictedBytes,
                total,
            )
        }
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val COVER_CACHE_DIR = "cover_cache"
        const val SETTINGS_VERSION = 2
        const val GRID_MAX_EDGE = 512
        const val DETAIL_MAX_EDGE = 768
        const val WEBP_QUALITY = 88
        const val JPEG_QUALITY = 90
        const val WEBP_SLOW_MS = 250L
        const val MAX_CACHE_BYTES = 300L * 1024L * 1024L
        const val MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024
        const val NEGATIVE_TTL_MS = 7L * 24L * 60L * 60L * 1000L
        private const val TELEMETRY_PREFS = "cover_cache_telemetry"
        private const val KEY_EVICTED_FILES = "evicted_files"
        private const val KEY_EVICTED_BYTES = "evicted_bytes"

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .addInterceptor(SecureCoverInterceptor)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}
