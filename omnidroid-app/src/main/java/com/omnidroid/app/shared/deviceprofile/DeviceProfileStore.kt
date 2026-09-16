package com.omnidroid.app.shared.deviceprofile

import android.content.Context
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

class DeviceProfileStore(context: Context) {
    private val prefs = SharedPreferencesHelper.getSharedPreferences(context.applicationContext)

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun getProfile(): DeviceProfile? {
        val raw = prefs.getString(KEY_PROFILE_JSON, null) ?: return null
        return try {
            json.decodeFromString(DeviceProfile.serializer(), raw)
        } catch (t: Throwable) {
            Timber.w(t, "Failed to decode device profile")
            null
        }
    }

    fun saveProfile(profile: DeviceProfile) {
        prefs.edit()
            .putString(KEY_PROFILE_JSON, json.encodeToString(profile))
            .putBoolean(KEY_INITIAL_CAPTURE_DONE, true)
            .apply()
    }

    fun updateProfile(transform: (DeviceProfile) -> DeviceProfile) {
        val current = getProfile() ?: DeviceProfile()
        saveProfile(transform(current))
    }

    fun isInitialCaptureDone(): Boolean = prefs.getBoolean(KEY_INITIAL_CAPTURE_DONE, false)

    fun isBenchmarkPromptShown(): Boolean = prefs.getBoolean(KEY_BENCHMARK_PROMPT_SHOWN, false)

    fun setBenchmarkPromptShown(shown: Boolean = true) {
        prefs.edit().putBoolean(KEY_BENCHMARK_PROMPT_SHOWN, shown).apply()
    }

    companion object {
        private const val KEY_PROFILE_JSON = "device_profile_json"
        private const val KEY_INITIAL_CAPTURE_DONE = "device_profile_initial_capture_done"
        private const val KEY_BENCHMARK_PROMPT_SHOWN = "device_profile_benchmark_prompt_shown"
    }
}
