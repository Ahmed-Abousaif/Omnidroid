package com.omnidroid.lib.saves

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class SaveFileRenamerTest {
    @Test
    fun renamesSaveRamAndStates() {
        val root = createTempDirectory("save-rename").toFile()
        try {
            val saves = File(root, "saves").also { it.mkdirs() }
            val states = File(root, "states").also { it.mkdirs() }
            val statesCore = File(states, "core").also { it.mkdirs() }
            val previews = File(root, "previews").also { it.mkdirs() }
            val previewsCore = File(previews, "core").also { it.mkdirs() }
            File(saves, "Old Game.srm").writeText("sram")
            File(statesCore, "Old Game.nes.state").writeText("state")
            File(statesCore, "Old Game.nes.state.metadata").writeText("{}")
            File(statesCore, "Old Game.nes.slot1").writeText("slot")
            File(previewsCore, "Old Game.nes.slot1.jpg").writeText("jpg")

            assertTrue(
                SaveFileRenamer.renameArtifacts(
                    saves,
                    states,
                    previews,
                    "Old Game.nes",
                    "New Game.nes",
                ),
            )
            assertTrue(File(saves, "New Game.srm").exists())
            assertFalse(File(saves, "Old Game.srm").exists())
            assertTrue(File(statesCore, "New Game.nes.state").exists())
            assertTrue(File(statesCore, "New Game.nes.slot1").exists())
            assertTrue(File(previewsCore, "New Game.nes.slot1.jpg").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun refusesCollision() {
        val root = createTempDirectory("save-collide").toFile()
        try {
            val saves = File(root, "saves").also { it.mkdirs() }
            File(saves, "Old.srm").writeText("a")
            File(saves, "New.srm").writeText("b")
            assertFalse(
                SaveFileRenamer.renameArtifacts(
                    saves,
                    File(root, "states").also { it.mkdirs() },
                    File(root, "previews").also { it.mkdirs() },
                    "Old.nes",
                    "New.nes",
                ),
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
