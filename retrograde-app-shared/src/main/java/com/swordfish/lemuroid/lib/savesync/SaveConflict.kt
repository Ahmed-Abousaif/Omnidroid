package com.swordfish.lemuroid.lib.savesync

import kotlinx.serialization.Serializable

enum class ConflictResolution {
    USE_LOCAL,
    USE_CLOUD,
    KEEP_BOTH,
}

@Serializable
data class SaveConflict(
    val id: String,
    val providerId: String,
    val folder: String,
    val relativePath: String,
    val localPath: String,
    val remoteCopyPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
