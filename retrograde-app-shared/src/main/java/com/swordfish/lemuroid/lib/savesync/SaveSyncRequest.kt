package com.swordfish.lemuroid.lib.savesync

import com.swordfish.lemuroid.lib.library.CoreID

data class SaveSyncRequest(
    val cores: Set<CoreID> = emptySet(),
    val gameFileName: String? = null,
    val includeSaves: Boolean = true,
    val includeStates: Boolean = true,
    val includePreviews: Boolean = true,
    val excludedFileNames: Set<String> = emptySet(),
    val alwaysFileNames: Set<String> = emptySet(),
)
