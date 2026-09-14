package com.swordfish.lemuroid.lib.savesync

import android.content.Context
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

class GameFrameSpeedPreferences(context: Context) {
    private val prefs = SharedPreferencesHelper.getSharedPreferences(context)

    fun get(gameId: Int): Int {
        val stored = prefs.getInt(key(gameId), DEFAULT_SPEED)
        return if (stored in ALLOWED_SPEEDS) stored else DEFAULT_SPEED
    }

    fun set(
        gameId: Int,
        speed: Int,
    ) {
        val sanitized = if (speed in ALLOWED_SPEEDS) speed else DEFAULT_SPEED
        prefs.edit().putInt(key(gameId), sanitized).apply()
    }

    private fun key(gameId: Int) = "frame_speed_$gameId"

    companion object {
        const val DEFAULT_SPEED = 1
        val ALLOWED_SPEEDS = setOf(1, 2, 4, 8, 16)
    }
}
