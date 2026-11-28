package com.omnidroid.app.shared.ra

import org.junit.Assert.assertEquals
import org.junit.Test

class RetroAchievementsUserAgentTest {
    @Test
    fun formatUserAgentStripsDebugSuffixAndEmbedsPlatformAndCore() {
        assertEquals(
            "Omnidroid/3.2.0 (Android) omnidroid_ra/1.0.0 rcheevos/12.5.0",
            RetroAchievementsCoordinator.formatUserAgentHeader(
                appVersion = "3.2.0-DEBUG",
                moduleVersion = "1.0.0",
                rcheevosVersion = "12.5.0",
                coreLabel = null,
            ),
        )

        assertEquals(
            "Omnidroid/3.2.0 (Android; snes9x/libsnes9x_libretro_android.so) omnidroid_ra/1.0.0 rcheevos/12.5.0",
            RetroAchievementsCoordinator.formatUserAgentHeader(
                appVersion = "3.2.0",
                moduleVersion = "1.0.0",
                rcheevosVersion = "12.5.0",
                coreLabel = "snes9x/libsnes9x_libretro_android.so",
            ),
        )
    }
}
