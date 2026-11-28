package com.omnidroid.lib.bios

import android.content.Context
import android.content.ContextWrapper
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.storage.DirectoriesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class LegacyBiosTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context: Context get() =
        object : ContextWrapper(null) {
            override fun getFilesDir() = temporary.root
        }
    private val directories get() = DirectoriesManager(context)

    private fun missing(id: String): List<String> {
        val game =
            Game(
                fileName = "game",
                fileUri = "file:///game",
                title = "Game",
                systemId = id,
                developer = null,
                coverFrontUrl = null,
                lastIndexedAt = 0,
            )
        return BiosManager(directories).getMissingBiosFiles(GameSystem.findById(id).systemCoreConfigs.single(), game)
    }

    @Test fun msx2RequiresBothRomsEvenWhenOneIsAlreadyPresent() {
        assertEquals(listOf("MSX2.ROM", "MSX2EXT.ROM"), missing("msx2"))
        File(directories.getSystemDirectory(), "MSX2.ROM").writeText("present")
        assertEquals(listOf("MSX2EXT.ROM"), missing("msx2"))
        File(directories.getSystemDirectory(), "MSX2EXT.ROM").writeText("present")
        assertTrue(missing("msx2").isEmpty())
    }

    @Test fun operaUsesTheEmbeddedBootRomWithoutExternalFirmware() {
        assertTrue(missing("3do").isEmpty())
        val config = GameSystem.findById("3do").systemCoreConfigs.single()
        assertEquals("opera-boot-internal", config.defaultSettings.single { it.key == "opera_bios" }.value)
    }

    @Test fun embeddedFirmwareDoesNotBlockBootAndRequiredFirmwareIsRecognized() {
        listOf("atari5200", "atari800", "c64", "apple2", "jaguar").forEach { assertTrue(missing(it).isEmpty()) }
        assertEquals(listOf("saturn_bios.bin"), missing("saturn"))
        assertEquals(listOf("colecovision.rom"), missing("colecovision"))
        listOf("SATURN_BIOS.BIN", "colecovision.rom", "MSX2EXT.ROM", "ATARIXL.ROM").forEach {
            assertTrue(BiosManager.isKnownBiosName(it))
        }
    }
}
