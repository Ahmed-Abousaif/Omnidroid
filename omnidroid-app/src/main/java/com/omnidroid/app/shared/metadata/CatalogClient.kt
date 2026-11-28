package com.omnidroid.app.shared.metadata

import com.omnidroid.lib.library.metadata.catalog.CatalogBatch
import com.omnidroid.lib.library.metadata.catalog.CatalogChanges
import com.omnidroid.lib.library.metadata.catalog.CatalogIdentity
import com.omnidroid.lib.library.metadata.catalog.CatalogResponse
import com.omnidroid.lib.library.metadata.catalog.CatalogSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class CatalogUnavailable(
    val retryAt: Long,
) : IOException("Metadata service temporarily unavailable")

@Singleton
class CatalogClient internal constructor(
    private val settings: MetadataSettings,
    private val client: OkHttpClient,
    private val baseUrl: String,
) {
    @Inject
    constructor(settings: MetadataSettings) : this(settings, defaultClient(), settings.baseUrl)

    val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }

    suspend fun resolve(identities: List<CatalogIdentity>): CatalogResponse =
        request("resolve", json.encodeToString(CatalogBatch(identities)))

    suspend fun search(identity: CatalogIdentity): CatalogResponse =
        request("search", json.encodeToString(CatalogSearch(identity)))

    suspend fun changes(after: Long): CatalogChanges =
        withContext(Dispatchers.IO) {
            if (!settings.enabled ||
                !settings.configured
            ) {
                throw CatalogUnavailable(System.currentTimeMillis() + 3_600_000)
            }
            client
                .newCall(
                    Request
                        .Builder()
                        .url("$baseUrl/api/metadata/changes?after=$after")
                        .get()
                        .build(),
                ).execute()
                .use { response ->
                    if (!response.isSuccessful) throw CatalogUnavailable(System.currentTimeMillis() + 60_000)
                    json.decodeFromString<CatalogChanges>(
                        readBody(response.body ?: throw IOException("Empty catalog response")),
                    )
                }
        }

    private suspend fun request(
        path: String,
        payload: String,
    ): CatalogResponse =
        withContext(Dispatchers.IO) {
            if (!settings.enabled ||
                !settings.configured
            ) {
                throw CatalogUnavailable(System.currentTimeMillis() + 3_600_000)
            }
            val request =
                Request
                    .Builder()
                    .url("$baseUrl/api/metadata/$path")
                    .header("Accept", "application/json")
                    .header("User-Agent", "Omnidroid-metadata/1")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val seconds = response.header("Retry-After")?.toLongOrNull()?.coerceIn(30, 86_400) ?: 60
                    throw CatalogUnavailable(System.currentTimeMillis() + seconds * 1000)
                }
                val body = response.body ?: throw CatalogUnavailable(System.currentTimeMillis() + 60_000)
                val result = json.decodeFromString<CatalogResponse>(readBody(body))
                if (result.schemaVersion != 1) throw IOException("Unsupported metadata schema")
                result
            }
        }

    private fun readBody(body: ResponseBody): String {
        if (body.contentLength() > 2_000_000) throw IOException("Oversized metadata response")
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        body.byteStream().use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > 2_000_000) throw IOException("Oversized metadata response")
                output.write(buffer, 0, count)
            }
        }
        return output.toString("UTF-8")
    }

    companion object {
        private fun defaultClient() =
            OkHttpClient
                .Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .callTimeout(100, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
    }
}
