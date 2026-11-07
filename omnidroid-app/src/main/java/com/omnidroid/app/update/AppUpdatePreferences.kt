package com.omnidroid.app.update

import android.content.Context
import android.content.SharedPreferences
import com.omnidroid.lib.preferences.SharedPreferencesHelper

class AppUpdatePreferences(
    context: Context,
) {
    private val prefs: SharedPreferences =
        SharedPreferencesHelper.getSharedPreferences(context.applicationContext)

    var skippedVersion: String?
        get() = prefs.getString(KEY_SKIPPED_VERSION, null)
        set(value) = prefs.edit().putString(KEY_SKIPPED_VERSION, value).apply()

    var pendingApkPath: String?
        get() = prefs.getString(KEY_PENDING_APK, null)
        set(value) = prefs.edit().putString(KEY_PENDING_APK, value).apply()

    var cachedAvailableVersion: String?
        get() = prefs.getString(KEY_CACHED_VERSION, null)
        set(value) = prefs.edit().putString(KEY_CACHED_VERSION, value).apply()

    var cachedAvailableNotes: String?
        get() = prefs.getString(KEY_CACHED_NOTES, null)
        set(value) = prefs.edit().putString(KEY_CACHED_NOTES, value).apply()

    var cachedApkUrl: String?
        get() = prefs.getString(KEY_CACHED_APK_URL, null)
        set(value) = prefs.edit().putString(KEY_CACHED_APK_URL, value).apply()

    var cachedSha256Url: String?
        get() = prefs.getString(KEY_CACHED_SHA_URL, null)
        set(value) = prefs.edit().putString(KEY_CACHED_SHA_URL, value).apply()

    var cachedSha256Notes: String?
        get() = prefs.getString(KEY_CACHED_SHA_NOTES, null)
        set(value) = prefs.edit().putString(KEY_CACHED_SHA_NOTES, value).apply()

    fun cacheAvailable(release: ParsedGitHubRelease) {
        cachedAvailableVersion = release.version
        cachedAvailableNotes = release.notes
        cachedApkUrl = release.apkDownloadUrl
        cachedSha256Url = release.sha256DownloadUrl
        cachedSha256Notes = release.sha256FromNotes
    }

    fun clearCachedAvailable() {
        prefs.edit()
            .remove(KEY_CACHED_VERSION)
            .remove(KEY_CACHED_NOTES)
            .remove(KEY_CACHED_APK_URL)
            .remove(KEY_CACHED_SHA_URL)
            .remove(KEY_CACHED_SHA_NOTES)
            .apply()
    }

    companion object {
        private const val KEY_SKIPPED_VERSION = "app_update_skipped_version"
        private const val KEY_PENDING_APK = "app_update_pending_apk"
        private const val KEY_CACHED_VERSION = "app_update_cached_version"
        private const val KEY_CACHED_NOTES = "app_update_cached_notes"
        private const val KEY_CACHED_APK_URL = "app_update_cached_apk_url"
        private const val KEY_CACHED_SHA_URL = "app_update_cached_sha_url"
        private const val KEY_CACHED_SHA_NOTES = "app_update_cached_sha_notes"
    }
}
