package com.swordfish.lemuroid.lib.savesync

data class RemoteSaveFile(
    val id: String,
    val relativePath: String,
    val size: Long,
    val modifiedTime: Long,
    val hash: String? = null,
)
