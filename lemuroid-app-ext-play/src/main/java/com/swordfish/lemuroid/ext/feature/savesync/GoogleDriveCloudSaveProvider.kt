package com.swordfish.lemuroid.ext.feature.savesync

import android.app.Activity
import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.FileContent
import com.google.api.client.util.DateTime
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.swordfish.lemuroid.ext.R
import com.swordfish.lemuroid.lib.savesync.CloudSaveFolder
import com.swordfish.lemuroid.lib.savesync.RemoteSaveFile
import timber.log.Timber
import java.io.File

class GoogleDriveCloudSaveProvider(
    private val appContext: Context,
) : CloudSaveProvider {
    override val id: String = PROVIDER_ID
    override val displayName: String = "Google Drive"

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
                .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
                .build()
        GoogleSignIn.getClient(appContext, options).signOut()
    }

    override fun listRemote(folder: CloudSaveFolder): List<RemoteSaveFile> {
        val drive = drive() ?: return emptyList()
        val folderId = getOrCreateAppDataFolder(drive, folder.remoteName)
        return getRemoteFiles(drive, folderId).toList()
    }

    override fun upload(
        folder: CloudSaveFolder,
        localFile: File,
        relativePath: String,
        existing: RemoteSaveFile?,
    ): RemoteSaveFile {
        val drive = drive() ?: throw IllegalStateException("Google Drive is not configured")
        val folderId = getOrCreateAppDataFolder(drive, folder.remoteName)
        val mediaContent = FileContent("application/x-binary", localFile)
        val metadata = com.google.api.services.drive.model.File()
        metadata.modifiedTime = DateTime(localFile.lastModified())

        val remoteId =
            if (existing != null) {
                drive.files().update(existing.id, metadata, mediaContent).execute().id
            } else {
                metadata.parents = listOf(folderId)
                metadata.name = localFile.name
                metadata.appProperties = mapOf(LOCAL_PATH to relativePath)
                drive.files().create(metadata, mediaContent).setFields("id").execute().id
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
        val drive = drive() ?: return
        dest.parentFile?.mkdirs()
        Timber.i("Downloading Drive file to $dest")
        dest.outputStream().use { output ->
            drive.files().get(remote.id).executeMediaAndDownloadTo(output)
        }
        dest.setLastModified(remote.modifiedTime)
    }

    override fun computeRemoteUsage(): RemoteUsage {
        val drive = drive() ?: return RemoteUsage(0, 0)
        var bytes = 0L
        var count = 0
        CloudSaveFolder.values().forEach { folder ->
            val folderId = runCatching { getOrCreateAppDataFolder(drive, folder.remoteName) }.getOrNull() ?: return@forEach
            getRemoteFiles(drive, folderId).forEach {
                bytes += it.size
                count += 1
            }
        }
        return RemoteUsage(bytes, count)
    }

    private fun drive(): Drive? = DriveFactory(appContext).create()

    private fun getOrCreateAppDataFolder(
        drive: Drive,
        folderName: String,
    ): String {
        val query =
            drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$folderName' and mimeType = 'application/vnd.google-apps.folder'")
                .setFields("files(id)")
                .execute()

        if (query.files.size > 0) {
            return query.files[0].id
        }

        val metadata = com.google.api.services.drive.model.File()
        metadata.parents = listOf("appDataFolder")
        metadata.name = folderName
        metadata.mimeType = "application/vnd.google-apps.folder"
        return drive.files().create(metadata).setFields("id").execute().id
    }

    private fun getRemoteFiles(
        drive: Drive,
        folderId: String,
    ): Sequence<RemoteSaveFile> {
        var pageToken: String? = null
        return sequence {
            do {
                val result =
                    drive.files().list()
                        .setPageSize(500)
                        .setSpaces("appDataFolder")
                        .setQ("'$folderId' in parents and trashed = false and mimeType = 'application/x-binary'")
                        .setFields("nextPageToken, files(id, name, size, appProperties, modifiedTime, md5Checksum)")
                        .setPageToken(pageToken)
                        .execute()

                result.files
                    .filter { it.appProperties?.get(LOCAL_PATH) != null }
                    .forEach { file ->
                        yield(
                            RemoteSaveFile(
                                id = file.id,
                                relativePath = file.appProperties[LOCAL_PATH]!!,
                                size = file.size?.toLong() ?: 0L,
                                modifiedTime = file.modifiedTime?.value ?: 0L,
                                hash = file.md5Checksum,
                            ),
                        )
                    }
                pageToken = result.nextPageToken
            } while (pageToken != null)
        }
    }

    companion object {
        const val PROVIDER_ID = "gdrive"
        const val LOCAL_PATH = "localPath"
    }
}
