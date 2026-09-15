package com.omnidroid.lib.savesync

import android.app.Activity

data class CloudSaveProviderInfo(
    val id: String,
    val displayName: String,
    val configured: Boolean,
    val accountLabel: String,
    val remoteUsage: String,
    val lastSync: String,
    val lastError: String?,
    val signInActivity: Class<out Activity>?,
)
