package com.omnidroid.lib.savesync

import android.content.Context
import com.omnidroid.lib.preferences.SharedPreferencesHelper

class ProviderSyncStateStore(context: Context) {
    private val prefs = SharedPreferencesHelper.getSharedPreferences(context)

    fun getLastSync(providerId: String): Long = prefs.getLong(lastSyncKey(providerId), 0L)

    fun setLastSync(
        providerId: String,
        timestamp: Long,
    ) {
        prefs.edit().putLong(lastSyncKey(providerId), timestamp).apply()
    }

    fun getLastError(providerId: String): String? = prefs.getString(lastErrorKey(providerId), null)

    fun setLastError(
        providerId: String,
        error: String?,
    ) {
        prefs.edit().putString(lastErrorKey(providerId), error).apply()
    }

    fun getLastUsage(providerId: String): String? = prefs.getString(lastUsageKey(providerId), null)

    fun setLastUsage(
        providerId: String,
        usage: String,
    ) {
        prefs.edit().putString(lastUsageKey(providerId), usage).apply()
    }

    fun clear(providerId: String) {
        prefs.edit()
            .remove(lastSyncKey(providerId))
            .remove(lastErrorKey(providerId))
            .remove(lastUsageKey(providerId))
            .apply()
    }

    private fun lastSyncKey(providerId: String) = "save_sync_last_$providerId"

    private fun lastErrorKey(providerId: String) = "save_sync_error_$providerId"

    private fun lastUsageKey(providerId: String) = "save_sync_usage_$providerId"
}
