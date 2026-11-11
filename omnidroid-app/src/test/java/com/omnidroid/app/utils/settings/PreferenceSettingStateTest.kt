package com.omnidroid.app.utils.settings

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenceSettingStateTest {
    private val values = mutableMapOf<String, Any>()
    private val editor = Proxy.newProxyInstance(
        SharedPreferences.Editor::class.java.classLoader,
        arrayOf(SharedPreferences.Editor::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "putInt" -> {
                values[args!![0] as String] = args[1]
                proxy
            }
            "apply" -> null
            else -> error(method.name)
        }
    } as SharedPreferences.Editor
    private val preferences = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader,
        arrayOf(SharedPreferences::class.java),
    ) { _, method, args ->
        when (method.name) {
            "edit" -> editor
            "getInt" -> (values[args!![0]] ?: args[1]) as Int
            "getBoolean" -> (values[args!![0]] ?: args[1]) as Boolean
            "getString" -> (values[args!![0]] ?: args[1]) as String
            else -> error(method.name)
        }
    } as SharedPreferences

    @Test
    fun writeExternalReloadAndResetKeepTheirDistinctSemantics() {
        val state = PreferenceSettingState(
            preferences, "quality", 2,
            { preferences.safeGetInt("quality", 2) },
            { edit, value -> edit.putInt("quality", value) },
        )
        assertEquals(2, state.value)
        assertEquals(emptyMap<String, Any>(), values)
        state.value = 0
        assertEquals(0, values["quality"])
        values["quality"] = 1
        state.reload()
        assertEquals(1, state.value)
        values.clear()
        state.reload()
        assertEquals(2, state.value)
        assertEquals(emptyMap<String, Any>(), values)
        state.reset()
        assertEquals(2, values["quality"])
    }

    @Test
    fun wrongStoredTypesFallBackWithoutOverwritingTheValue() {
        values["option"] = "old-value"
        assertEquals(6, preferences.safeGetInt("option", 6))
        assertEquals(false, preferences.safeGetBoolean("option", false))
        values["option"] = 123
        assertEquals("default", preferences.safeGetString("option", "default"))
        assertEquals(123, values["option"])
    }
}
