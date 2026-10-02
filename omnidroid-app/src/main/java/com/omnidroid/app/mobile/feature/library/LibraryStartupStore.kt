package com.omnidroid.app.mobile.feature.library

import android.content.Context
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.omnidroid.lib.library.MetaSystemID
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Stores which library view the app should open on. */
class LibraryStartupStore(context: Context) {
    private val preferences = SharedPreferencesHelper.getSharedPreferences(context)
    private val flowPreferences = FlowSharedPreferences(preferences)
    private val key = context.getString(com.omnidroid.R.string.pref_key_startup_destination)

    fun get(): LibraryFilter = decode(preferences.getString(key, ALL))

    fun observe(): Flow<LibraryFilter> {
        return flowPreferences.getString(key, ALL).asFlow().map { decode(it) }
    }

    fun set(filter: LibraryFilter) {
        preferences.edit().putString(key, encode(filter)).apply()
    }

    companion object {
        const val ALL = "all"
        const val FAVORITES = "favorites"
        private const val SYSTEM_PREFIX = "system:"

        fun encode(filter: LibraryFilter): String =
            when (filter) {
                is LibraryFilter.All -> ALL
                is LibraryFilter.Favorites -> FAVORITES
                is LibraryFilter.System -> SYSTEM_PREFIX + filter.metaSystemID.name
            }

        fun decode(raw: String?): LibraryFilter =
            when {
                raw == FAVORITES -> LibraryFilter.Favorites
                raw != null && raw.startsWith(SYSTEM_PREFIX) ->
                    raw.removePrefix(SYSTEM_PREFIX)
                        .takeIf { it.isNotBlank() }
                        ?.let { runCatching { LibraryFilter.System(MetaSystemID.valueOf(it)) }.getOrNull() }
                        ?: LibraryFilter.All
                else -> LibraryFilter.All
            }
    }
}