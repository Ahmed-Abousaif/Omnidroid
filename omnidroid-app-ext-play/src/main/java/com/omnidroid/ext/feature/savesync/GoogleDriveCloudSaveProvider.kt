package com.omnidroid.ext.feature.savesync

import android.app.Activity
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.omnidroid.ext.R
import com.omnidroid.lib.savesync.CloudSaveFolder
import com.omnidroid.lib.savesync.RemoteSaveFile
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GoogleDriveCloudSaveProvider(
    private val appContext: Context,
) : CloudSaveProvider {
    override val id: String = PROVIDER_ID
    override val displayName: String = "Google Drive"

    private val httpClient = OkHttpClient.Builder().build()

    override fun isConfigured(): Boolean = GoogleSignIn.getLastSignedInAccount(appContext) != null

    override fun getAccountLabel(): String {
        val email = GoogleSignIn.getLastSignedInAccount(appContext)?.email
        return if (email != null) {
            appContext.getString(R.string.gdrive_connected_summary, email)
        } else {
            appContext.getString(R.string.gdrive_connected_none_summary)
        }
    }

    override fun getSignInActivity(): Class<out Activity> = ActivateGoogleDriveActivity::class.java

    override fun signOut() {
        val options =
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestId()
                .requestEmail()
                .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
                .build()
        GoogleSignIn.getClient(appContext, options).signOut()
    }

    private fun getAuthToken(): String? {
        val account = GoogleSignIn.getLastSignedInAccount(appContext)?.account ?: return null
        return try {
            GoogleAuthUtil.getToken(appContext, account, "oauth2:$DRIVE_APPDATA_SCOPE")
        } catch (e: Exception) {
            Timber.e(e, "Failed to acquire Google Drive auth token")
            null
        }
    }

    override fun listRemote(folder: CloudSaveFolder): List<RemoteSaveFile> {
        val token = getAuthToken() ?: return emptyList()
        val folderId = getOrCreateAppDataFolder(token, folder.remoteName) ?: return emptyList()
        return getRemoteFiles(token, folderId).toList()
    }

    override fun upload(
        folder: CloudSaveFolder,
        localFile: File,
        relativePath: String,
        existing: RemoteSaveFile?,
    ): RemoteSaveFile {
        val token = getAuthToken() ?: throw IllegalStateException("Google Drive is not configured")
        val folderId = getOrCreateAppDataFolder(token, folder.remoteName)
            ?: throw IllegalStateException("Failed to get or create folder ${folder.remoteName}")

        val isoTime = formatIso8601(localFile.lastModified())

        val remoteId = if (existing != null) {
            val metadataJson = JSONObject().apply {
                put("modifiedTime", isoTime)
            }.toString()

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .addPart(localFile.asRequestBody("application/x-binary".toMediaType()))
                .build()

            val request = Request.Builder()
                .url("$UPLOAD_BASE_URL/files/${existing.id}?uploadType=multipart")
                .header("Authorization", "Bearer $token")
                .patch(multipartBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("Drive upload failed: ${response.code}")
                val json = JSONObject(response.body?.string().orEmpty())
                json.getString("id")
            }
        } else {
            val metadataJson = JSONObject().apply {
                put("name", localFile.name)
                put("parents", JSONArray().apply { put(folderId) })
                put("appProperties", JSONObject().apply { put(LOCAL_PATH, relativePath) })
                put("modifiedTime", isoTime)
            }.toString()

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .addPart(localFile.asRequestBody("application/x-binary".toMediaType()))
                .build()

            val request = Request.Builder()
                .url("$UPLOAD_BASE_URL/files?uploadType=multipart&fields=id")
                .header("Authorization", "Bearer $token")
                .post(multipartBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("Drive upload failed: ${response.code}")
                val json = JSONObject(response.body?.string().orEmpty())
                json.getString("id")
            }
        }

        return RemoteSaveFile(
            id = remoteId,
            relativePath = relativePath,
            size = localFile.length(),
            modifiedTime = localFile.lastModified(),
            hash = null,
        )
    }

    override fun download(
        remote: RemoteSaveFile,
        dest: File,
    ) {
        if (remote.size == 0L) return
        val token = getAuthToken() ?: return
        dest.parentFile?.mkdirs()
        Timber.i("Downloading Drive file to $dest")

        val request = Request.Builder()
            .url("$BASE_URL/files/${remote.id}?alt=media")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Drive download failed: ${response.code}")
            response.body?.byteStream()?.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        dest.setLastModified(remote.modifiedTime)
    }

    override fun computeRemoteUsage(): RemoteUsage {
        val token = getAuthToken() ?: return RemoteUsage(0, 0)
        var bytes = 0L
        var count = 0
        CloudSaveFolder.values().forEach { folder ->
            val folderId = runCatching { getOrCreateAppDataFolder(token, folder.remoteName) }.getOrNull() ?: return@forEach
            getRemoteFiles(token, folderId).forEach {
                bytes += it.size
                count += 1
            }
        }
        return RemoteUsage(bytes, count)
    }

    private fun getOrCreateAppDataFolder(
        token: String,
        folderName: String,
    ): String? {
        val q = "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder'"
        val url = "$BASE_URL/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", q)
            .addQueryParameter("fields", "files(id)")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    return files.getJSONObject(0).getString("id")
                }
            }
        }

        val createJson = JSONObject().apply {
            put("name", folderName)
            put("mimeType", "application/vnd.google-apps.folder")
            put("parents", JSONArray().apply { put("appDataFolder") })
        }.toString()

        val createRequest = Request.Builder()
            .url("$BASE_URL/files?fields=id")
            .header("Authorization", "Bearer $token")
            .post(createJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        httpClient.newCall(createRequest).execute().use { response ->
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                return json.optString("id").takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    private fun getRemoteFiles(
        token: String,
        folderId: String,
    ): Sequence<RemoteSaveFile> {
        return sequence {
            var pageToken: String? = null
            do {
                val q = "'$folderId' in parents and trashed = false and mimeType = 'application/x-binary'"
                val urlBuilder = "$BASE_URL/files".toHttpUrl().newBuilder()
                    .addQueryParameter("pageSize", "500")
                    .addQueryParameter("spaces", "appDataFolder")
                    .addQueryParameter("q", q)
                    .addQueryParameter("fields", "nextPageToken, files(id, name, size, appProperties, modifiedTime, md5Checksum)")
                if (pageToken != null) {
                    urlBuilder.addQueryParameter("pageToken", pageToken)
                }

                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string().orEmpty())
                        val files = json.optJSONArray("files")
                        if (files != null) {
                            for (i in 0 until files.length()) {
                                val item = files.getJSONObject(i)
                                val appProps = item.optJSONObject("appProperties")
                                val localPath = appProps?.optString(LOCAL_PATH)
                                if (!localPath.isNullOrEmpty()) {
                                    val id = item.getString("id")
                                    val size = item.optLong("size", 0L)
                                    val modTimeStr = item.optString("modifiedTime")
                                    val modTime = if (modTimeStr.isNotEmpty()) parseIso8601(modTimeStr) else 0L
                                    val hash = item.optString("md5Checksum").takeIf { it.isNotEmpty() }
                                    yield(
                                        RemoteSaveFile(
                                            id = id,
                                            relativePath = localPath,
                                            size = size,
                                            modifiedTime = modTime,
                                            hash = hash,
                                        ),
                                    )
                                }
                            }
                        }
                        pageToken = json.optString("nextPageToken").takeIf { it.isNotEmpty() }
                    } else {
                        pageToken = null
                    }
                }
            } while (pageToken != null)
        }
    }

    companion object {
        const val PROVIDER_ID = "gdrive"
        const val LOCAL_PATH = "localPath"
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val BASE_URL = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_BASE_URL = "https://www.googleapis.com/upload/drive/v3"

        private fun parseIso8601(isoString: String): Long {
            return try {
                java.time.Instant.parse(isoString).toEpochMilli()
            } catch (e: Throwable) {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                format.parse(isoString)?.time ?: System.currentTimeMillis()
            }
        }

        private fun formatIso8601(timeMillis: Long): String {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            return format.format(Date(timeMillis))
        }
    }
}
