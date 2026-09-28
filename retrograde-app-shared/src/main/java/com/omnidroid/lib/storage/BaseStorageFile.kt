package com.omnidroid.lib.storage

import android.net.Uri

data class BaseStorageFile(
    val name: String,
    val size: Long,
    val uri: Uri,
    val path: String? = null,
    val lastModified: Long = 0,
) {
    val extension: String
        get() = name.substringAfterLast('.', "").lowercase()

    val extensionlessName: String
        get() = name.substringBeforeLast('.', "")
}
