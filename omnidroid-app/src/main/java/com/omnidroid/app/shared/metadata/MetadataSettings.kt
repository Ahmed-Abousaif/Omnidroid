package com.omnidroid.app.shared.metadata

import android.content.Context
import com.omnidroid.BuildConfig
import com.omnidroid.R
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataSettings
    internal constructor(
        private val context: Context,
        private val enabledOverride: Boolean?,
    ) {
        @Inject
        constructor(
            @ApplicationContext context: Context,
        ) : this(context, null)

        private val prefs get() = SharedPreferencesHelper.getSharedPreferences(context)
        val enabled
            get() = enabledOverride ?: prefs.getBoolean(context.getString(R.string.pref_key_online_artwork), true)
        val wifiOnly get() = prefs.getBoolean(context.getString(R.string.pref_key_artwork_wifi_only), true)
        val baseUrl get() = BuildConfig.METADATA_BASE_URL.trimEnd('/')
        val configured get() = baseUrl.startsWith("https://")
        var catalogRevision: Long
            get() = prefs.getLong("metadata_catalog_revision", 0)
            set(value) {
                prefs.edit().putLong("metadata_catalog_revision", value).apply()
            }
        var cursor: Int
            get() = prefs.getInt("metadata_enrichment_cursor", 0)
            set(value) {
                prefs.edit().putInt("metadata_enrichment_cursor", value).apply()
            }
    }
