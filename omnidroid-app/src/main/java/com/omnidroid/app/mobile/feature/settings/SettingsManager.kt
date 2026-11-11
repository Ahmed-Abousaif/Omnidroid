package com.omnidroid.app.mobile.feature.settings

import android.content.Context
import android.content.SharedPreferences
import com.omnidroid.app.utils.settings.safeGetBoolean
import com.omnidroid.app.utils.settings.safeGetInt
import com.omnidroid.app.utils.settings.safeGetString
import com.omnidroid.app.utils.settings.safeGetStringSet
import com.omnidroid.R
import com.omnidroid.app.shared.settings.HDModeQuality
import com.omnidroid.app.shared.settings.HdModeBatteryMonitor
import com.omnidroid.common.math.Fraction
import com.omnidroid.lib.storage.cache.CacheCleaner
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsManager(private val context: Context, sharedPreferences: Lazy<SharedPreferences>) {
    private val sharedPreferences by lazy { sharedPreferences.get() }

    private fun getString(resId: Int) = context.getString(resId)

    suspend fun autoSave() = booleanPreference(R.string.pref_key_autosave, true)

    suspend fun hapticFeedbackMode() = stringPreference(R.string.pref_key_haptic_feedback_mode, "press")

    suspend fun vibrationIntensity() = floatPreference(R.string.pref_key_vibration_intensity, 100, 50)

    suspend fun virtualPadOpacity() = floatPreference(R.string.pref_key_virtual_pad_opacity, 100, 100)

    suspend fun lowLatencyAudio() = booleanPreference(R.string.pref_key_low_latency_audio, true)

    suspend fun screenFilter() =
        stringPreference(
            R.string.pref_key_shader_filter,
            context.resources.getStringArray(R.array.pref_key_shader_filter_values).first(),
        )

    suspend fun hdMode(): Boolean {
        if (!HdModeBatteryMonitor.isAllowed(context)) return false
        return booleanPreference(R.string.pref_key_hd_mode, false)
    }

    suspend fun hdModeQuality() = HDModeQuality.parse(intPreference(R.string.pref_key_hd_mode_quality, 1))

    suspend fun tiltSensitivity() = floatPreference(R.string.pref_key_tilt_sensitivity_index, 10, 6)

    suspend fun autoSaveSync() = booleanPreference(R.string.pref_key_save_sync_auto, false)

    suspend fun syncSaves() = booleanPreference(R.string.pref_key_save_sync_enable, false)

    suspend fun syncStatesCores() = stringSetPreference(R.string.pref_key_save_sync_cores, setOf())

    suspend fun syncBeforeGame() = booleanPreference(R.string.pref_key_save_sync_before_game, true)

    suspend fun autoSaveSyncInterval() = stringPreference(R.string.pref_key_save_sync_interval, "3h")

    suspend fun enableRumble() = booleanPreference(R.string.pref_key_enable_rumble, false)

    suspend fun enableDeviceRumble() = booleanPreference(R.string.pref_key_enable_device_rumble, false)

    suspend fun enableImmersiveMode() = booleanPreference(R.string.pref_key_enable_immersive_mode, false)

    suspend fun cacheSizeBytes() =
        stringPreference(
            R.string.pref_key_max_cache_size,
            CacheCleaner.getDefaultCacheLimit().toString(),
        )

    suspend fun allowDirectGameLoad() = booleanPreference(R.string.pref_key_allow_direct_game_load, true)

    private suspend fun booleanPreference(
        keyId: Int,
        default: Boolean,
    ): Boolean =
        withContext(Dispatchers.IO) {
            sharedPreferences.safeGetBoolean(getString(keyId), default)
        }

    private suspend fun stringPreference(
        keyId: Int,
        default: String,
    ): String =
        withContext(Dispatchers.IO) {
            sharedPreferences.safeGetString(getString(keyId), default) ?: default
        }

    private suspend fun stringSetPreference(
        keyId: Int,
        default: Set<String>,
    ): Set<String> =
        withContext(Dispatchers.IO) {
            sharedPreferences.safeGetStringSet(getString(keyId), default)?.toSet() ?: default
        }

    private suspend fun floatPreference(
        keyId: Int,
        denominator: Int,
        defaultNumerator: Int,
    ): Float =
        withContext(Dispatchers.IO) {
            Fraction(sharedPreferences.safeGetInt(getString(keyId), defaultNumerator), denominator).floatValue
        }

    private suspend fun intPreference(
        keyId: Int,
        default: Int,
    ): Int =
        withContext(Dispatchers.IO) {
            sharedPreferences.safeGetInt(getString(keyId), default)
        }
}
