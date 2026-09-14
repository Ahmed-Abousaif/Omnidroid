package com.swordfish.lemuroid.ext.feature.savesync

import android.app.Activity
import android.content.Context
import com.swordfish.lemuroid.ext.R
import com.swordfish.lemuroid.lib.savesync.CloudSaveFolder
import com.swordfish.lemuroid.lib.savesync.RemoteSaveFile
import okhttp3.FormBody
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

class OneDriveCloudSaveProvider(
    private val appContext: Context,
) : CloudSaveProvider {
    override val id: String = PROVIDER_ID
    override val displayName: String = "OneDrive"

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

    override fun getSignInActivity(): Class<out Activity> = ActivateOneDriveActivity::class.java

    override fun signOut() {
        tokens.clear()
    }

    override fun listRemote(folder: CloudSaveFolder): List<RemoteSaveFile> {
        ensureFolder(folder.remoteName)
        return listRecursive(folder.remoteName, "")
    }

    override fun upload(
        folder: CloudSaveFolder,
        localFile: File,
        relativePath: String,
        existing: RemoteSaveFile?,
    ): RemoteSaveFile {
        val itemPath = "${folder.remoteName}/$relativePath"
        val url = "$GRAPH/me/drive/special/approot:/${encodePath(itemPath)}:/content"
        val request =
            Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${accessToken()}")
                .put(localFile.asRequestBody("application/octet-stream".toMediaType()))
                .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(body.ifBlank { response.message })
            val json = JSONObject(body)
            patchModifiedTime(json.optString("id"), localFile.lastModified())
            return RemoteSaveFile(
                id = json.optString("id"),
                relativePath = relativePath,
                size = localFile.length(),
                modifiedTime = localFile.lastModified(),
                hash = json.optJSONObject("file")?.optJSONObject("hashes")?.optString("sha1Hash"),
            )
        }
    }

    override fun download(
        remote: RemoteSaveFile,
        dest: File,
    ) {
        dest.parentFile?.mkdirs()
        val request =
            Request.Builder()
                .url("$GRAPH/me/drive/items/${remote.id}/content")
                .addHeader("Authorization", "Bearer ${accessToken()}")
                .get()
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

    private fun listRecursive(
        folderPath: String,
        relativePrefix: String,
    ): List<RemoteSaveFile> {
        val files = mutableListOf<RemoteSaveFile>()
        var url: String? = "$GRAPH/me/drive/special/approot:/${encodePath(folderPath)}:/children"
        while (url != null) {
            val currentUrl = url ?: break
            val json = authorizedGet(currentUrl)
            val values = json.optJSONArray("value") ?: break
            for (i in 0 until values.length()) {
                val item = values.getJSONObject(i)
                val name = item.optString("name")
                val childRelative = if (relativePrefix.isEmpty()) name else "$relativePrefix/$name"
                if (item.has("folder")) {
                    files += listRecursive("$folderPath/$name", childRelative)
                } else {
                    files +=
                        RemoteSaveFile(
                            id = item.optString("id"),
                            relativePath = childRelative,
                            size = item.optLong("size"),
                            modifiedTime = parseTime(item.optJSONObject("fileSystemInfo")?.optString("lastModifiedDateTime").orEmpty()),
                            hash = item.optJSONObject("file")?.optJSONObject("hashes")?.optString("sha1Hash"),
                        )
                }
            }
            url = json.optString("@odata.nextLink").ifBlank { null }
        }
        return files
    }

    private fun ensureFolder(name: String) {
        val check = runCatching { authorizedGet("$GRAPH/me/drive/special/approot:/${encodePath(name)}") }
        if (check.isSuccess) return
        val body =
            JSONObject()
                .put("name", name)
                .put("folder", JSONObject())
                .put("@microsoft.graph.conflictBehavior", "fail")
                .toString()
        val request =
            Request.Builder()
                .url("$GRAPH/me/drive/special/approot/children")
                .addHeader("Authorization", "Bearer ${accessToken()}")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
        http.newCall(request).execute().close()
    }

    private fun patchModifiedTime(
        itemId: String,
        millis: Long,
    ) {
        if (itemId.isBlank()) return
        val body =
            JSONObject()
                .put(
                    "fileSystemInfo",
                    JSONObject().put("lastModifiedDateTime", formatTime(millis)),
                )
                .toString()
        val request =
            Request.Builder()
                .url("$GRAPH/me/drive/items/$itemId")
                .addHeader("Authorization", "Bearer ${accessToken()}")
                .addHeader("Content-Type", "application/json")
                .patch(body.toRequestBody("application/json".toMediaType()))
                .build()
        runCatching { http.newCall(request).execute().close() }
    }

    private fun authorizedGet(url: String): JSONObject {
        val request =
            Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${accessToken()}")
                .get()
                .build()
        http.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(payload.ifBlank { response.message })
            return JSONObject(payload)
        }
    }

    private fun accessToken(): String {
        val current = tokens.read() ?: throw IllegalStateException("OneDrive is not configured")
        val refresh = current.refreshToken
        if (refresh.isNullOrBlank()) return current.accessToken
        return runCatching { refreshAccessToken(refresh) }.getOrDefault(current.accessToken)
    }

    private fun refreshAccessToken(refreshToken: String): String {
        val body =
            FormBody.Builder()
                .add("client_id", CloudSaveConfig.oneDriveClientId)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("scope", ActivateOneDriveActivity.SCOPE)
                .build()
        val request =
            Request.Builder()
                .url("https://login.microsoftonline.com/common/oauth2/v2.0/token")
                .post(body)
                .build()
        http.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(payload)
            val json = JSONObject(payload)
            val updated =
                OAuthTokens(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.optString("refresh_token").ifBlank { refreshToken },
                    accountLabel = tokens.read()?.accountLabel ?: "OneDrive",
                )
            tokens.write(updated)
            return updated.accessToken
        }
    }

    private fun encodePath(path: String): String = path.split("/").joinToString("/") { UriEncode(it) }

    private fun UriEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun parseTime(value: String): Long {
        if (value.isBlank()) return 0L
        return runCatching { ISO.parse(value)?.time ?: 0L }.getOrDefault(0L)
    }

    private fun formatTime(millis: Long): String = ISO.format(Date(millis))

    companion object {
        const val PROVIDER_ID = "onedrive"
        private const val GRAPH = "https://graph.microsoft.com/v1.0"
        private val ISO =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
    }
}
