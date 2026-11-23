package com.omnidroid.lib.backend

import com.omnidroid.lib.backend.ps3.Ps3GraphicsOptions
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.SystemID
import org.junit.Assert.*
import org.junit.Test

class Ps3GraphicsOptionsTest {
    @Test fun readsHostTogglePreferencesAndRejectsInvalidChoices() {
        val toggle = Ps3GraphicsOptions.all.first { it.kind == Ps3GraphicsOptions.Kind.BOOL }
        assertEquals("enabled", toggle.storedValue(true))
        assertEquals("disabled", toggle.storedValue(false))
        assertEquals("enabled", toggle.storedValue("enabled"))
        assertEquals(toggle.default, toggle.storedValue("invalid"))
        val scale = Ps3GraphicsOptions.all.first { it.key == "ps3_resolution_scale" }
        assertEquals("75", scale.storedValue("75"))
        assertEquals("100", scale.storedValue("Resolution scale; 100|75|50"))
    }
    @Test fun onlyCuratedGraphicsOptionsAreExposedAndDefaultsAreValid() {
        val options = Ps3GraphicsOptions.all
        assertEquals(options.size, options.map { it.key }.distinct().size)
        assertTrue(options.all { it.path.startsWith("Video@@") && it.accepts(it.default) })
        val config = GameSystem.findById(SystemID.PS3.dbname).systemCoreConfigs.first()
        assertEquals(options.map { it.key }.toSet(), (config.exposedSettings + config.exposedAdvancedSettings).map { it.key }.toSet())
        assertTrue(config.statesSupported)
        assertFalse(config.autoSaveSupported)
        assertTrue(config.exposedAdvancedSettings.none { it.key.contains("cpu") || it.key.contains("audio") || it.key.contains("debug") })
    }
}
