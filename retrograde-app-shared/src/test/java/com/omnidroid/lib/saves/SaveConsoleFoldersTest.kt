package com.omnidroid.lib.saves

import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.SystemID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveConsoleFoldersTest {
    @Test
    fun everySystemIdMapsToUniqueSafeFolderName() {
        val folderNames = SystemID.values().map { SaveConsoleFolders.forSystem(it) }
        assertEquals(SystemID.values().size, folderNames.distinct().size)
        val forbidden = Regex("""[\\/:*?"<>|]""")
        folderNames.forEach { name ->
            assertTrue(name.isNotBlank())
            assertFalse("Invalid character in $name", forbidden.containsMatchIn(name))
            assertFalse(CoreID.values().any { it.coreName == name })
        }
    }

    @Test
    fun resolvesConsoleScopedPathsFromDbUniqueCoreAndExtensions() {
        val byStem = mapOf("FireRed" to setOf("gba"), "Tetris" to setOf("gb", "nes"))
        val byFile = mapOf("FireRed.gba" to setOf("gba"))

        assertEquals(
            "saves/GBA/FireRed.srm",
            SaveConsoleFolders.toConsoleScopedPath("saves/FireRed.srm", byStem, byFile),
        )
        assertEquals(
            "saves/PS3/ps3-BLUS12345.srm",
            SaveConsoleFolders.toConsoleScopedPath("saves/ps3-BLUS12345.srm", emptyMap(), emptyMap()),
        )
        // Ambiguous stem without unique match stays in saves/
        assertEquals(
            "saves/Tetris.srm",
            SaveConsoleFolders.toConsoleScopedPath("saves/Tetris.srm", byStem, byFile),
        )
        // Unique-core state resolves even with empty DB
        assertEquals(
            "states/NES/fceumm/Mario.nes.slot1",
            SaveConsoleFolders.toConsoleScopedPath("states/fceumm/Mario.nes.slot1", emptyMap(), emptyMap()),
        )
        // Shared-core state resolves via unique ROM extension
        assertEquals(
            "states/GBC/gambatte/Zelda.gbc.slot2",
            SaveConsoleFolders.toConsoleScopedPath("states/gambatte/Zelda.gbc.slot2", emptyMap(), emptyMap()),
        )
        assertEquals(
            "state-previews/GBA/mgba/FireRed.gba.slot1.jpg",
            SaveConsoleFolders.toConsoleScopedPath(
                "state-previews/mgba/FireRed.gba.slot1.jpg",
                emptyMap(),
                emptyMap(),
            ),
        )
    }

    @Test
    fun unwrapsConsoleFoldersWhenConvertingToInternalPaths() {
        assertEquals("saves/FireRed.srm", SaveConsoleFolders.toInternalPath("saves/GBA/FireRed.srm"))
        assertEquals(
            "states/mgba/FireRed.gba.slot1",
            SaveConsoleFolders.toInternalPath("states/GBA/mgba/FireRed.gba.slot1"),
        )
        assertEquals(
            "state-previews/mgba/FireRed.gba.slot1.jpg",
            SaveConsoleFolders.toInternalPath("state-previews/GBA/mgba/FireRed.gba.slot1.jpg"),
        )
        // Non-console subfolders are preserved untouched
        assertEquals("saves/custom_sub/FireRed.srm", SaveConsoleFolders.toInternalPath("saves/custom_sub/FireRed.srm"))
    }
}
