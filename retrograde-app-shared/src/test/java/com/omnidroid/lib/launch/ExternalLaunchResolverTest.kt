package com.omnidroid.lib.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalLaunchResolverTest {

    @Test
    fun resolvesFromDataOnly() {
        val result = ExternalLaunchResolver.resolve(
            dataString = "content://com.android.externalstorage.documents/document/primary%3ARoms%2Fnes%2Fmario.nes",
            getExtra = { null },
        )

        assertNotNull(result)
        assertEquals("content://com.android.externalstorage.documents/document/primary%3ARoms%2Fnes%2Fmario.nes", result!!.uriString)
        assertEquals("content://com.android.externalstorage.documents/document/primary%3ARoms%2Fnes%2Fmario.nes", result.normalizedUriString)
        assertEquals("/storage/emulated/0/Roms/nes/mario.nes", result.canonicalPath)
        assertNull(result.systemExtra)
    }

    @Test
    fun dataTakesPrecedenceOverExtras() {
        val extras = mapOf(
            "ROM" to "/storage/emulated/0/Roms/snes/zelda.sfc",
            "SYSTEM" to "snes",
        )
        val result = ExternalLaunchResolver.resolve(
            dataString = "file:///storage/emulated/0/Roms/nes/mario.nes",
            getExtra = { extras[it] },
        )

        assertNotNull(result)
        assertEquals("file:///storage/emulated/0/Roms/nes/mario.nes", result!!.uriString)
        assertEquals("snes", result.systemExtra)
    }

    @Test
    fun resolvesEachExtraInAbsenceOfData() {
        for (extraKey in ExternalLaunchResolver.EXTRAS_ORDER) {
            val result = ExternalLaunchResolver.resolve(
                dataString = null,
                getExtra = { if (it == extraKey) "/storage/emulated/0/Roms/test.rom" else null },
            )
            assertNotNull("Should resolve extra: $extraKey", result)
            assertEquals("file:///storage/emulated/0/Roms/test.rom", result!!.normalizedUriString)
        }
    }

    @Test
    fun resolvesPrecedenceAmongExtras() {
        // ROM > bootPath > GamePath > PATH > AutoStartFile > uri
        val extras = mapOf(
            "bootPath" to "/storage/emulated/0/bootPath.rom",
            "PATH" to "/storage/emulated/0/PATH.rom",
            "uri" to "file:///storage/emulated/0/uri.rom",
        )
        val result = ExternalLaunchResolver.resolve(
            dataString = null,
            getExtra = { extras[it] },
        )

        assertNotNull(result)
        assertEquals("/storage/emulated/0/bootPath.rom", result!!.uriString)
    }

    @Test
    fun returnsNullForEmptyOrBlankInputs() {
        assertNull(ExternalLaunchResolver.resolve(null) { null })
        assertNull(ExternalLaunchResolver.resolve("") { null })
        assertNull(ExternalLaunchResolver.resolve("   ") { null })
        assertNull(ExternalLaunchResolver.resolve(null) { "" })
        assertNull(ExternalLaunchResolver.resolve(null) { "   " })
    }

    @Test
    fun extractsSystemExtraWhenPresent() {
        val result = ExternalLaunchResolver.resolve(
            dataString = "file:///storage/emulated/0/game.chd",
            getExtra = { if (it == "SYSTEM") "psx" else null },
        )

        assertNotNull(result)
        assertEquals("psx", result!!.systemExtra)
    }
}
