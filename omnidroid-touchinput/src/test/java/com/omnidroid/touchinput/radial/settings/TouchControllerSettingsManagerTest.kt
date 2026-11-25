package com.omnidroid.touchinput.radial.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TouchControllerSettingsManagerTest {
    @Test
    fun legacyJsonWithoutOpacityDecodesWithNullOverride() =
        runTest {
            val prefs = InMemorySharedPreferences()
            prefs
                .edit()
                .putString(
                    "touch_controller_settings_${TouchControllerID.GBA}_" +
                        "${TouchControllerSettingsManager.Orientation.LANDSCAPE.ordinal}",
                    """{"scale":0.7,"rotation":0.0,"marginX":0.1,"marginY":0.2,"allowDiagonals":true,"dpadStyle":"CROSS","screenPositionY":0.5}""",
                ).commit()

            val manager = TouchControllerSettingsManager(prefs)
            val settings =
                manager
                    .observeSettings(
                        TouchControllerID.GBA,
                        TouchControllerSettingsManager.Orientation.LANDSCAPE,
                    ).first()

            assertEquals(0.7f, settings.scale, 0.0001f)
            assertNull(settings.opacity)
        }

    @Test
    fun perOrientationOpacityPersistsIndependentlyIncludingFullOpacityAndResetsToNull() =
        runTest {
            val prefs = InMemorySharedPreferences()
            val manager = TouchControllerSettingsManager(prefs)

            manager.storeSettings(
                TouchControllerID.PSP,
                TouchControllerSettingsManager.Orientation.LANDSCAPE,
                TouchControllerSettingsManager.Settings(opacity = 0.35f),
            )
            manager.storeSettings(
                TouchControllerID.PSP,
                TouchControllerSettingsManager.Orientation.PORTRAIT,
                TouchControllerSettingsManager.Settings(opacity = 1.0f),
            )

            val reloaded = TouchControllerSettingsManager(prefs)
            val landscape =
                reloaded
                    .observeSettings(
                        TouchControllerID.PSP,
                        TouchControllerSettingsManager.Orientation.LANDSCAPE,
                    ).first()
            val portrait =
                reloaded
                    .observeSettings(
                        TouchControllerID.PSP,
                        TouchControllerSettingsManager.Orientation.PORTRAIT,
                    ).first()
            val untouched =
                reloaded
                    .observeSettings(
                        TouchControllerID.SNES,
                        TouchControllerSettingsManager.Orientation.LANDSCAPE,
                    ).first()

            assertEquals(0.35f, landscape.opacity ?: -1f, 0.0001f)
            assertEquals(1.0f, portrait.opacity ?: -1f, 0.0001f)
            assertNull(untouched.opacity)

            reloaded.resetSettings(
                TouchControllerID.PSP,
                TouchControllerSettingsManager.Orientation.LANDSCAPE,
            )
            val resetLandscape =
                reloaded
                    .observeSettings(
                        TouchControllerID.PSP,
                        TouchControllerSettingsManager.Orientation.LANDSCAPE,
                    ).first()
            assertNull(resetLandscape.opacity)
        }

    @Test
    fun corruptStoredSettingsFallBackToDefaults() =
        runTest {
            val prefs = InMemorySharedPreferences()
            prefs
                .edit()
                .putString(
                    "touch_controller_settings_${TouchControllerID.N64}_" +
                        "${TouchControllerSettingsManager.Orientation.PORTRAIT.ordinal}",
                    "{not-valid-json",
                ).commit()

            val manager = TouchControllerSettingsManager(prefs)
            val settings =
                manager
                    .observeSettings(
                        TouchControllerID.N64,
                        TouchControllerSettingsManager.Orientation.PORTRAIT,
                    ).first()

            assertEquals(TouchControllerSettingsManager.DEFAULT_SCALE, settings.scale, 0.0001f)
            assertNull(settings.opacity)
        }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()

        override fun getString(
            key: String?,
            defValue: String?,
        ): String? = (data[key] as? String) ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(
            key: String?,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? = (data[key] as? Set<String>)?.toMutableSet() ?: defValues

        override fun getInt(
            key: String?,
            defValue: Int,
        ): Int = (data[key] as? Int) ?: defValue

        override fun getLong(
            key: String?,
            defValue: Long,
        ): Long = (data[key] as? Long) ?: defValue

        override fun getFloat(
            key: String?,
            defValue: Float,
        ): Float = (data[key] as? Float) ?: defValue

        override fun getBoolean(
            key: String?,
            defValue: Boolean,
        ): Boolean = (data[key] as? Boolean) ?: defValue

        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = EditorImpl()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        private inner class EditorImpl : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(
                key: String?,
                value: String?,
            ): SharedPreferences.Editor =
                apply {
                    if (key != null) pending[key] = value
                }

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?,
            ): SharedPreferences.Editor =
                apply {
                    if (key != null) pending[key] = values?.toSet()
                }

            override fun putInt(
                key: String?,
                value: Int,
            ): SharedPreferences.Editor =
                apply {
                    if (key != null) pending[key] = value
                }

            override fun putLong(
                key: String?,
                value: Long,
            ): SharedPreferences.Editor =
                apply {
                    if (key != null) pending[key] = value
                }

            override fun putFloat(
                key: String?,
                value: Float,
            ): SharedPreferences.Editor =
                apply {
                    if (key != null) pending[key] = value
                }

            override fun putBoolean(
                key: String?,
                value: Boolean,
            ): SharedPreferences.Editor =
                apply {
                    if (key != null) pending[key] = value
                }

            override fun remove(key: String?): SharedPreferences.Editor =
                apply {
                    if (key != null) removals += key
                }

            override fun clear(): SharedPreferences.Editor =
                apply {
                    clearAll = true
                }

            override fun commit(): Boolean {
                if (clearAll) data.clear()
                removals.forEach { data.remove(it) }
                pending.forEach { (k, v) ->
                    if (v == null) data.remove(k) else data[k] = v
                }
                return true
            }

            override fun apply() {
                commit()
            }
        }
    }
}
