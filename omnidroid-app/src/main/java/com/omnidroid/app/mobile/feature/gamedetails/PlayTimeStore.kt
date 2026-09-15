package com.omnidroid.app.mobile.feature.gamedetails

import android.content.Context
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.flow.Flow

class PlayTimeStore(context: Context) {
    private val preferences = SharedPreferencesHelper.getSharedPreferences(context)
    private val flowPreferences = FlowSharedPreferences(preferences)

    fun get(gameId: Int): Long = preferences.getLong(key(gameId), 0L)

    fun add(
        gameId: Int,
        durationMs: Long,
    ) {
        if (durationMs <= 0L) return
        preferences.edit().putLong(key(gameId), get(gameId) + durationMs).apply()
    }

    fun observe(gameId: Int): Flow<Long> = flowPreferences.getLong(key(gameId), 0L).asFlow()

    private fun key(gameId: Int) = "play_time_$gameId"
}
