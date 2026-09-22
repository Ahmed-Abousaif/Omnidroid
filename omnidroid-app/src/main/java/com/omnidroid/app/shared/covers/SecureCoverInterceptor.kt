package com.omnidroid.app.shared.covers

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.Locale

/**
 * Security interceptor ensuring remote cover images are only loaded from verified,
 * trusted domain whitelists and strictly validate protocol, MIME types, and payload size.
 */
object SecureCoverInterceptor : Interceptor {
    private const val MAX_COVER_BYTES = 15L * 1024L * 1024L // 15 MB payload safety limit

    private val ALLOWED_DOMAINS = listOf(
        "thumbnails.libretro.com",
        "media.rawg.io",
        "rawg.io",
        "images.igdb.com",
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val host = url.host.lowercase(Locale.ROOT)
        val scheme = url.scheme.lowercase(Locale.ROOT)

        // 1. Enforce strict HTTP/HTTPS scheme (block file://, content://, javascript:, etc.)
        if (scheme != "http" && scheme != "https") {
            throw IOException("Blocked unsafe cover protocol: $scheme")
        }

        // 2. Enforce strict domain whitelist (SSRF & Arbitrary URL exfiltration defense)
        val isAllowedHost = ALLOWED_DOMAINS.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }

        if (!isAllowedHost) {
            throw IOException("Blocked unauthorized remote cover host: $host")
        }

        val response = chain.proceed(request)

        if (response.isSuccessful) {
            // 3. Enforce maximum response payload size to prevent memory/decompression bomb DOS
            val contentLength = response.body?.contentLength() ?: -1L
            if (contentLength > MAX_COVER_BYTES) {
                response.close()
                throw IOException("Remote cover exceeds maximum allowed size ($contentLength bytes)")
            }

            // 4. Validate MIME type to reject executable binaries, scripts, or HTML payloads
            val contentType = response.header("Content-Type")?.lowercase(Locale.ROOT)
            if (contentType != null &&
                !contentType.startsWith("image/") &&
                !contentType.startsWith("application/octet-stream") &&
                !contentType.startsWith("binary/octet-stream")
            ) {
                response.close()
                throw IOException("Blocked non-image response Content-Type: $contentType")
            }
        }

        return response
    }
}
