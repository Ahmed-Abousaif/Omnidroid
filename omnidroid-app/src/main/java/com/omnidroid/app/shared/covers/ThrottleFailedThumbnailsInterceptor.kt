package com.omnidroid.app.shared.covers

import android.util.LruCache
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

object ThrottleFailedThumbnailsInterceptor : Interceptor {
    private val missingUntil = LruCache<String, Long>(512)

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestUrl = chain.request().url.toString()
        val until = missingUntil[requestUrl]
        if (until != null && until > System.currentTimeMillis()) {
            throw IOException("Artwork resource is temporarily recorded as missing")
        }

        val response = chain.proceed(chain.request())
        if (response.code == 404 || response.code == 410) {
            missingUntil.put(requestUrl, System.currentTimeMillis() + 86_400_000)
        }

        return response
    }
}
