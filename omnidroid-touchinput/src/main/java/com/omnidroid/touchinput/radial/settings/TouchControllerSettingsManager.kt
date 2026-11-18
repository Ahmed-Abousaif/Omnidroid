package com.omnidroid.touchinput.radial.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import timber.log.Timber

class TouchControllerSettingsManager(
    private val sharedPreferences: SharedPreferences,
    legacyPadOpacityKey: String? = null,
) {
    enum class Orientation {
        PORTRAIT,
        LANDSCAPE,
    }

    @Serializable
    enum class DpadStyle {
        CROSS,
        ANALOG,
    }

    @Serializable
    data class Settings(
        val scale: Float = DEFAULT_SCALE,
        val rotation: Float = DEFAULT_ROTATION,
        val marginX: Float = DEFAULT_MARGIN_X,
        val marginY: Float = DEFAULT_MARGIN_Y,
        val allowDiagonals: Boolean = DEFAULT_ALLOW_DIAGONALS,
        val dpadStyle: DpadStyle = DEFAULT_DPAD_STYLE,
        val screenPositionY: Float = DEFAULT_SCREEN_POSITION_Y,
        val opacity: Float = DEFAULT_OPACITY,
    )

    init {
        legacyPadOpacityKey?.let(::migrateLegacyPadOpacity)
    }

    private val cachedSettings = mutableMapOf<String, MutableStateFlow<Settings?>>()

    private fun migrateLegacyPadOpacity(legacyPadOpacityKey: String) {
        if (!sharedPreferences.contains(legacyPadOpacityKey)) return
        val legacyOpacity =
            runCatching { sharedPreferences.getInt(legacyPadOpacityKey, 100) }
                .getOrNull()
                ?.coerceIn(0, 100)
                ?.div(100f)
                ?: return

        val keysToMigrate =
            TouchControllerID.entries.flatMap { id ->
                Orientation.entries.map { orientation -> getPreferenceString(id, orientation) }
            }

        sharedPreferences.edit {
            keysToMigrate.forEach { key ->
                val raw = sharedPreferences.getString(key, null)
                val migrated =
                    when {
                        raw == null -> null
                        rawContainsOpacity(raw) -> null
                        else ->
                            runCatching {
                                Json
                                    .decodeFromString(Settings.serializer(), raw)
                                    .copy(opacity = legacyOpacity)
                            }.getOrNull()
                    }
                if (migrated != null) {
                    putString(key, Json.encodeToString(Settings.serializer(), migrated))
                }
            }
            remove(legacyPadOpacityKey)
        }
        Timber.d("Migrated legacy virtual pad opacity into per-orientation settings")
    }

    private fun rawContainsOpacity(raw: String): Boolean =
        runCatching {
            Json.parseToJsonElement(raw).jsonObject.containsKey("opacity")
        }.getOrDefault(false)

    fun observeSettings(
        touchControllerID: TouchControllerID,
        orientation: Orientation,
    ): Flow<Settings> {
        // Display cutout insets are applied by the caller as layout padding, so the
        // defaults stay at zero. Deriving margins from the cutout here used to double
        // up with that padding, and could not stop a user raising `scale` from pushing
        // the pads under the camera cutout.
        val defaultSettings =
            Settings(
                scale = DEFAULT_SCALE,
                rotation = DEFAULT_ROTATION,
                marginX = DEFAULT_MARGIN_X,
                marginY = DEFAULT_MARGIN_Y,
                screenPositionY = DEFAULT_SCREEN_POSITION_Y,
                opacity = DEFAULT_OPACITY,
            )
        val settingsKey = getPreferenceString(touchControllerID, orientation)
        val cachedStateFlow =
            cachedSettings.getOrPut(settingsKey) {
                val currentSettings =
                    sharedPreferences
                        .getString(settingsKey, null)
                        ?.let {
                            runCatching { Json.decodeFromString(Settings.serializer(), it) }
                                .getOrNull()
                        }

                MutableStateFlow(currentSettings)
            }
        return cachedStateFlow.map { it ?: defaultSettings }
    }

    suspend fun storeSettings(
        touchControllerID: TouchControllerID,
        orientation: Orientation,
        settings: Settings,
    ) {
        Timber.d("Updating touch settings for $touchControllerID at $orientation to $settings")
        updateCachedSettings(touchControllerID, orientation, settings)
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                putString(
                    getPreferenceString(touchControllerID, orientation),
                    Json.encodeToString(Settings.serializer(), settings),
                )
            }
        }
    }

    private fun updateCachedSettings(
        touchControllerID: TouchControllerID,
        orientation: Orientation,
        settings: Settings?,
    ) {
        val cacheKey = getPreferenceString(touchControllerID, orientation)
        val cacheFlow = cachedSettings.getOrPut(cacheKey) { MutableStateFlow(settings) }
        cacheFlow.value = settings
    }

    suspend fun resetSettings(
        touchControllerID: TouchControllerID,
        orientation: Orientation,
    ) {
        updateCachedSettings(touchControllerID, orientation, null)
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                remove(getPreferenceString(touchControllerID, orientation))
            }
        }
    }

    companion object {
        const val DEFAULT_SCALE = 0.5f
        const val DEFAULT_ROTATION = 0.0f
        const val DEFAULT_MARGIN_X = 0.0f
        const val DEFAULT_MARGIN_Y = 0.0f
        const val DEFAULT_ALLOW_DIAGONALS = true
        val DEFAULT_DPAD_STYLE = DpadStyle.CROSS
        const val DEFAULT_SCREEN_POSITION_Y = 0.5f
        const val DEFAULT_OPACITY = 1.0f

        const val MAX_ROTATION = 45f
        const val MIN_SCALE = 0.75f
        const val MAX_SCALE = 1.5f

        const val MAX_MARGINS = 96f
        const val MAX_MARGIN_Y_PORTRAIT = 480f
    }

    private fun getPreferenceString(
        controllerID: TouchControllerID,
        orientation: Orientation,
    ): String = "touch_controller_settings_${controllerID}_${orientation.ordinal}"
}
