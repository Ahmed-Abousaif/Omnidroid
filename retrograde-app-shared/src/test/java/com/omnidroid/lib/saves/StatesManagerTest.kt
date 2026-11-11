package com.omnidroid.lib.saves

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StatesManagerTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var directories: DirectoriesManager
    private lateinit var manager: StatesManager
    private val core = CoreID.FCEUMM
    private val game =
        Game(
            fileName = "Game (USA).nes",
            fileUri = "content://games/1",
            title = "Game",
            systemId = "nes",
            developer = null,
            coverFrontUrl = null,
            lastIndexedAt = 0,
        )

    @Before
    fun setup() {
        val context =
            object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
                override fun getExternalFilesDir(type: String?): File = temporary.root
            }
        directories = DirectoriesManager(context)
        manager = StatesManager(directories)
    }

    @Test
    fun keepsFourDefaultSlotsAndAddsSlotsOnlyForTheirGameAndCore() =
        runBlocking {
            assertEquals(listOf(0, 1, 2, 3), manager.getSavedSlotsInfo(game, core).map { it.index })
            assertTrue(manager.getSavedSlotsInfo(game, core).none { it.saveInfo.exists })
            assertEquals(4, manager.setNewSlotSave(game, core, state(4)))
            assertEquals(5, manager.setNewSlotSave(game, core, state(5)))
            assertEquals(
                listOf(0, 1, 2, 3, 4, 5),
                StatesManager(directories).getSavedSlotsInfo(game, core).map { it.index },
            )
            assertEquals(4, manager.getSavedSlotsInfo(game.copy(fileName = "Other.nes"), core).size)
            assertEquals(4, manager.getSavedSlotsInfo(game, CoreID.SNES9X).size)
            assertArrayEquals(state(4).state, manager.getSlotSave(game, core, 4)!!.state)
        }

    @Test
    fun discoversImportedSparseSlotsWithoutInventingEmptySlotsOrMatchingSidecars() =
        runBlocking {
            manager.setSlotSave(game, state(12), core, 12)
            val directory = File(directories.getStatesDirectory(), core.coreName)
            File(directory, "${game.fileName}.slot99.metadata").writeText("{}")
            File(directory, "${game.fileName}.slot100.jpg").writeText("preview")
            File(directory, "Other.nes.slot200").writeText("other game")
            File(directory, "${game.fileName}.slot0").writeText("invalid")
            assertEquals(listOf(0, 1, 2, 3, 12), manager.getSavedSlotsInfo(game, core).map { it.index })
            assertEquals(13, manager.setNewSlotSave(game, core, state(13)))
        }

    @Test
    fun concurrentNewSavesNeverReplaceAnExistingSlot() =
        runBlocking {
            manager.setSlotSave(game, state(1), core, 0)
            val slots =
                (1..8).map { number ->
                    async { manager.setNewSlotSave(game, core, state(number)) }
                }.awaitAll()
            assertEquals((4..11).toList(), slots.sorted())
            slots.forEachIndexed { index, slot ->
                assertArrayEquals(state(index + 1).state, manager.getSlotSave(game, core, slot)!!.state)
            }
            assertArrayEquals(state(1).state, manager.getSlotSave(game, core, 0)!!.state)
        }

    @Test
    fun autosaveAndManualStatesKeepSeparateContentsAndMetadata() =
        runBlocking {
            val auto = SaveState(byteArrayOf(2, 3), SaveState.Metadata(diskIndex = 2, version = 7))
            manager.setAutoSave(game, core, auto)
            manager.setSlotSave(game, state(1), core, 0)
            assertArrayEquals(auto.state, manager.getAutoSave(game, core)!!.state)
            assertEquals(auto.metadata, manager.getAutoSave(game, core)!!.metadata)
            assertArrayEquals(state(1).state, manager.getSlotSave(game, core, 0)!!.state)
            assertTrue(manager.getAutoSaveInfo(game, core).exists)
        }

    @Test
    fun missingStatesAreAbsentButCorruptStatesAndMetadataReportFailure() =
        runBlocking {
            assertNull(manager.getSlotSave(game, core, 0))
            assertNull(manager.getAutoSave(game, core))
            assertFalse(manager.getAutoSaveInfo(game, core).exists)
            manager.setSlotSave(game, state(1), core, 0)
            val directory = File(directories.getStatesDirectory(), core.coreName)
            File(directory, "${game.fileName}.slot1.metadata").writeText("invalid JSON")
            assertTrue(runCatching { manager.getSlotSave(game, core, 0) }.isFailure)
            File(directory, "${game.fileName}.slot1.metadata").delete()
            assertEquals(SaveState.Metadata(), manager.getSlotSave(game, core, 0)!!.metadata)
            File(directory, "${game.fileName}.state").writeBytes(byteArrayOf(0x1f, 0x8b.toByte(), 0))
            assertTrue(runCatching { manager.getAutoSave(game, core) }.isFailure)
        }

    @Test
    fun failedWritesAreReportedAndEmptyCapturesDoNotReplaceStates() =
        runBlocking {
            manager.setAutoSave(game, core, state(1))
            assertTrue(
                runCatching {
                    manager.setAutoSave(
                        game,
                        core,
                        SaveState(byteArrayOf(), SaveState.Metadata()),
                    )
                }.isFailure,
            )
            assertArrayEquals(state(1).state, manager.getAutoSave(game, core)!!.state)
            // A file where a core directory should be makes the destination unwritable on every platform.
            File(directories.getStatesDirectory(), CoreID.SNES9X.coreName).writeText("blocked")
            assertTrue(runCatching { manager.setSlotSave(game, state(2), CoreID.SNES9X, 0) }.isFailure)
            assertTrue(runCatching { manager.setSlotSave(game, state(2), core, -1) }.isFailure)
            assertNotNull(manager.getAutoSave(game, core))
        }

    @Test
    fun saveRamWriteFailureIsReported() =
        runBlocking {
            File(temporary.root, "saves").writeText("blocked")
            assertTrue(runCatching { SavesManager(directories).setSaveRAM(game, byteArrayOf(1)) }.isFailure)
        }

    private fun state(number: Int) = SaveState(byteArrayOf(number.toByte()), SaveState.Metadata())
}
