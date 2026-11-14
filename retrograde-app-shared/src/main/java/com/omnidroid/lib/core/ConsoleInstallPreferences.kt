package com.omnidroid.lib.core

import android.content.Context
import android.content.SharedPreferences
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.omnidroid.lib.library.MetaSystemID
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Explicit uninstall choices survive rescans and are scoped to the selected console. */
class ConsoleInstallPreferences(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(SharedPreferencesHelper.getSharedPreferences(context.applicationContext))

    fun uninstalled(): Set<MetaSystemID> = decode(preferences.getStringSet(KEY, emptySet()).orEmpty())

    fun observe(): Flow<Set<MetaSystemID>> =
        FlowSharedPreferences(preferences).getStringSet(KEY, emptySet()).asFlow().map(::decode)

    fun setUninstalled(console: MetaSystemID, uninstalled: Boolean) {
        val current = preferences.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        if (uninstalled) current.add(console.name) else current.remove(console.name)
        check(preferences.edit().putStringSet(KEY, current).commit()) { "Could not save console installation choice" }
    }

    private fun decode(names: Set<String>): Set<MetaSystemID> =
        names.mapNotNull { runCatching { MetaSystemID.valueOf(it) }.getOrNull() }.toSet()

    private companion object {
        const val KEY = "manually_uninstalled_consoles"
    }
}
