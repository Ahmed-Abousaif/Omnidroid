package com.omnidroid.ext.feature.savesync

import android.app.Activity
import android.content.Context
import com.omnidroid.ext.R
import com.omnidroid.lib.savesync.CloudSaveFolder
import com.omnidroid.lib.savesync.RemoteSaveFile
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DropboxCloudSaveProvider(
    private val appContext: Context,
) : CloudSaveProvider {
    override val id: String = PROVIDER_ID
    override val displayName: String = "Dropbox"

    private val tokens = OAuthTokenStore(appContext, PROVIDER_ID)
    private val http = OkHttpClient()

    override fun isConfigured(): Boolean = tokens.read() != null

    override fun getAccountLabel(): String {
        val label = tokens.read()?.accountLabel
        return if (label != null) {
            appContext.getString(R.string.gdrive_connected_summary, label)
        } else {
            appContext.getString(R.string.gdrive_connected_none_summary)
        }
    }

    override fun getSignInActivity(): Class<out Activity> = ActivateDropboxActivity::class.java

    override fun signOut() {
        tokens.clear()
    }

    override fun listRemote(folder: CloudSaveFolder): List<RemoteSaveFile> {
        val root = "/${folder.remoteName}"
        val files = mutableListOf<RemoteSaveFile>()
        var cursor: String? = null
        var hasMore = true
        while (hasMore) {
            val payload =
                if (cursor == null) {
                    JSONObject()
                        .put("path", root)
                        .put("recursive", true)
                        .put("include_deleted", false)
                        .toString()
                } else {
                    JSONObject().put("cursor", cursor).toString()
                }
            val url =
                if (cursor == null) {
                    "https://api.dropboxapi.com/2/files/list_folder"
                } else {
                    "https://api.dropboxapi.com/2/files/list_folder/continue"
                }
            val json = authorizedJson(url, payload)
            val entries = json.optJSONArray("entries") ?: break
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                if (entry.optString(".tag") != "file") continue
                val fullPath = entry.optString("path_display")
                val path = fullPath.removePrefix(root).trimStart('/')
                if (path.isEmpty()) continue
                files +=
                    RemoteSaveFile(
                        id = fullPath,
                        relativePath = path,
                        size = entry.optLong("size"),
                        modifiedTime = parseTime(entry.optString("client_modified").ifBlank { entry.optString("server_modified") }),
                        hash = entry.optString("content_hash").ifBlank { null },
                    )
            }
            hasMore = json.optBoolean("has_more")
            cursor = json.optString("cursor").ifBlank { null }
        }
        return files
    }

    override fun upload(
        folder: CloudSaveFolder,
        localFile: File,
        relativePath: String,
        existing: RemoteSaveFile?,
    ): RemoteSaveFile {
        val path = "/${folder.remoteName}/$relativePath"
        val arg =
            JSONObject()
                .put("path", path)
                .put("mode", "overwrite")
                .put("mute", true)
                .put("client_modified", formatTime(localFile.lastModified()))
                .toString()
        val request =
            Request.Builder()
                .url("https://content.dropboxapi.com/2/files/upload")
                .addHeader("Authorization", "Bearer ${accessToken()}")
                .addHeader("Dropbox-API-Arg", arg)
                .addHeader("Content-Type", "application/octet-stream")
                .post(localFile.asRequestBody("application/octet-stream".toMediaType()))
                .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(body.ifBlank { response.message })
            val json = JSONObject(body)
            return RemoteSaveFile(
                id = json.optString("id", path),
                relativePath = relativePath,
                size = localFile.length(),
                modifiedTime = localFile.lastModified(),
                hash = json.optString("content_hash").ifBlank { null },
            )
        }
    }

    override fun download(
        remote: RemoteSaveFile,
        dest: File,
    ) {
        dest.parentFile?.mkdirs()
        val arg = JSONObject().put("path", remote.id).toString()
        val request =
            Request.Builder()
                .url("https://content.dropboxapi.com/2/files/download")
                .addHeader("Authorization", "Bearer ${accessToken()}")
                .addHeader("Dropbox-API-Arg", arg)
                .post(ByteArray(0).toRequestBody(null))
                .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(response.body?.string() ?: response.message)
            }
            dest.outputStream().use { output ->
                response.body?.byteStream()?.copyTo(output)
            }
        }
        dest.setLastModified(remote.modifiedTime)
    }

    override fun computeRemoteUsage(): RemoteUsage {
        var bytes = 0L
        var count = 0
        CloudSaveFolder.values().forEach { folder ->
            runCatching { listRemote(folder) }.getOrDefault(emptyList()).forEach {
                bytes += it.size
                count += 1
            }
        }
        return RemoteUsage(bytes, count)
    }

    private fun authorizedJson(
        url: String,
        jsonBody: String,
    ): JSONObject {
        val request =
            Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${accessToken()}")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()
        http.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 409 && payload.contains("path/not_found")) {
                    return JSONObject().put("entries", org.json.JSONArray()).put("has_more", false)
                }
                throw IllegalStateException(payload.ifBlank { response.message })
            }
            return JSONObject(payload)
        }
    }

    private fun accessToken(): String {
        val current = tokens.read() ?: throw IllegalStateException("Dropbox is not configured")
        return current.accessToken
    }

    private fun parseTime(value: String): Long {
        if (value.isBlank()) return 0L
        return runCatching { ISO.parse(value)?.time ?: 0L }.getOrDefault(0L)
    }

    private fun formatTime(millis: Long): String = ISO.format(Date(millis))

    companion object {
        const val PROVIDER_ID = "dropbox"
        private val ISO =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
    }
}
