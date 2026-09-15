package com.omnidroid.lib.savesync

data class ProviderSyncResult(
    val providerId: String,
    val success: Boolean,
    val error: String? = null,
)

data class SaveSyncResult(
    val providerResults: List<ProviderSyncResult> = emptyList(),
    val conflicts: List<SaveConflict> = emptyList(),
)
