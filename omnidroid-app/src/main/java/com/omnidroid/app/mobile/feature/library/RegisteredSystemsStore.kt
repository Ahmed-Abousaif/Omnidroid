package com.omnidroid.app.mobile.feature.library

import android.content.Context
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.omnidroid.lib.library.MetaSystemID
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RegisteredSystemsStore(context: Context) {
    private val preferences = SharedPreferencesHelper.getSharedPreferences(context)
    private val flowPreferences = FlowSharedPreferences(preferences)

    fun get(): Set<MetaSystemID> {
        return preferences.getStringSet(KEY, emptySet())
            ?.mapNotNull { name -> runCatching { MetaSystemID.valueOf(name) }.getOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    fun observe(): Flow<Set<MetaSystemID>> {
        return flowPreferences.getStringSet(KEY, emptySet()).asFlow()
            .map { names ->
                names.mapNotNull { name ->
                    runCatching { MetaSystemID.valueOf(name) }.getOrNull()
                }.toSet()
            }
    }

    fun register(metaSystemID: MetaSystemID) {
        val updated =
            preferences.getStringSet(KEY, emptySet())
                ?.toMutableSet()
                ?: mutableSetOf()
        updated.add(metaSystemID.name)
        preferences.edit().putStringSet(KEY, updated).apply()
    }

    companion object {
        private const val KEY = "pref_registered_meta_systems"
    }
}
