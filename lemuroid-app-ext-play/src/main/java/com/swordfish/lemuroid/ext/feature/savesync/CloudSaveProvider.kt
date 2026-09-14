package com.swordfish.lemuroid.ext.feature.savesync

import android.app.Activity
import com.swordfish.lemuroid.lib.savesync.CloudSaveFolder
import com.swordfish.lemuroid.lib.savesync.RemoteSaveFile
import java.io.File

data class RemoteUsage(
    val bytes: Long,
    val fileCount: Int,
)

interface CloudSaveProvider {
    val id: String
    val displayName: String

    fun isConfigured(): Boolean

    fun getAccountLabel(): String

    fun getSignInActivity(): Class<out Activity>?

    fun signOut()

    fun listRemote(folder: CloudSaveFolder): List<RemoteSaveFile>

    fun upload(
        folder: CloudSaveFolder,
        localFile: File,
        relativePath: String,
        existing: RemoteSaveFile?,
    ): RemoteSaveFile

    fun download(
        remote: RemoteSaveFile,
        dest: File,
    )

    fun computeRemoteUsage(): RemoteUsage
}
