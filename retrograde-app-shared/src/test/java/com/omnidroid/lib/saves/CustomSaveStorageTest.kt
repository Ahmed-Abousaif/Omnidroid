package com.omnidroid.lib.saves

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.omnidroid.lib.R
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import com.omnidroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.runBlocking
import org.junit.After
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
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CustomSaveStorageTest {
    @get:Rule val temporary = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var directories: DirectoriesManager
    private lateinit var db: RetrogradeDatabase
    private lateinit var customStorage: CustomSaveStorage
    private lateinit var savesManager: SavesManager
    private lateinit var statesManager: StatesManager
    private lateinit var previewManager: StatesPreviewManager
    private lateinit var customFolder: File

    private val game =
        Game(
            id = 1,
            fileName = "Pokemon - Fire Red (USA).gba",
            fileUri = "content://games/1",
            title = "Pokemon",
            systemId = "gba",
            developer = null,
            coverFrontUrl = null,
            lastIndexedAt = 0,
        )

    private val systemCoreConfig =
        SystemCoreConfig(
            coreID = CoreID.MGBA,
            controllerConfigs = hashMapOf(),
            statesSupported = true,
        )

    @Before
    fun setup() {
        val appCtx = ApplicationProvider.getApplicationContext<Context>()
        val externalDir = temporary.newFolder("external")
        context =
            object : ContextWrapper(appCtx) {
                override fun getExternalFilesDir(type: String?): File = externalDir
            }
        // Clear any leftover preferences
        SharedPreferencesHelper
            .getLegacySharedPreferences(context)
            .edit()
            .clear()
            .commit()

        db =
            Room
                .inMemoryDatabaseBuilder(context, RetrogradeDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        db.gameDao().insert(listOf(game))

        customFolder = temporary.newFolder("custom_saves")
        directories = DirectoriesManager(context)
        customStorage = CustomSaveStorage(context, directories, db.gameDao())
        savesManager = SavesManager(directories, customStorage)
        statesManager = StatesManager(directories, customStorage)
        previewManager = StatesPreviewManager(directories, customStorage)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun isNotConfiguredByDefault() {
        assertFalse(customStorage.isConfigured())
        assertNull(customStorage.getCustomSavesFolderUriString())
        assertNull(customStorage.getDisplayName())
    }

    @Test
    fun isConfiguredWhenPrefSet() {
        setCustomFolderPref(customFolder.absolutePath)
        assertTrue(customStorage.isConfigured())
        assertEquals(customFolder.name, customStorage.getDisplayName())

        customStorage.clear()
        assertFalse(customStorage.isConfigured())
    }

    @Test
    fun readsAndWritesToCustomFolder() {
        setCustomFolderPref(customFolder.absolutePath)
        val data = byteArrayOf(1, 2, 3, 4, 5)

        val writeSuccess = customStorage.writeSave("test.srm", data)
        assertTrue(writeSuccess)

        val writtenFile = File(customFolder, "test.srm")
        assertTrue(writtenFile.exists())
        assertEquals(5, writtenFile.length())

        val readData = customStorage.readSave("test.srm")
        assertNotNull(readData)
        assertArrayEquals(data, readData)

        val info = customStorage.getSaveInfo("test.srm")
        assertNotNull(info)
        assertTrue(info!!.exists)
        assertTrue(info.date > 0)

        val listed = customStorage.listSaveFiles()
        assertEquals(listOf("test.srm"), listed)

        val renamed = customStorage.renameSave("test.srm", "renamed.srm")
        assertTrue(renamed)
        assertFalse(File(customFolder, "test.srm").exists())
        assertTrue(File(customFolder, "renamed.srm").exists())

        val deleted = customStorage.deleteSave("renamed.srm")
        assertTrue(deleted)
        assertFalse(File(customFolder, "renamed.srm").exists())
    }

    @Test
    fun savesManagerWritesOnlyToCustomFolderWhenConfiguredNoReplication() {
        runBlocking {
            setCustomFolderPref(customFolder.absolutePath)
            val sram = byteArrayOf(10, 20, 30)

            savesManager.setSaveRAM(game, sram)

            // Custom folder has the save under the console subfolder
            val customFile = File(customFolder, "saves/GBA/Pokemon - Fire Red (USA).srm")
            assertTrue(customFile.exists())
            assertArrayEquals(sram, customFile.readBytes())

            // Local internal storage must NOT have the save (zero replication)
            val localFile = File(directories.getSavesDirectory(), "Pokemon - Fire Red (USA).srm")
            assertFalse(localFile.exists())

            // Reading loads from custom storage and does not replicate to local storage
            val loaded = savesManager.getSaveRAM(game, systemCoreConfig)
            assertNotNull(loaded)
            assertArrayEquals(sram, loaded)
            assertFalse(localFile.exists())
        }
    }

    @Test
    fun savesManagerWritesToInternalWhenNotConfigured() {
        runBlocking {
            val sram = byteArrayOf(10, 20, 30)
            savesManager.setSaveRAM(game, sram)

            // Local internal storage has the save
            val localFile = File(directories.getSavesDirectory(), "Pokemon - Fire Red (USA).srm")
            assertTrue(localFile.exists())
            assertArrayEquals(sram, localFile.readBytes())
        }
    }

    @Test
    fun statesManagerWritesOnlyToCustomFolderWhenConfiguredNoReplication() {
        runBlocking {
            setCustomFolderPref(customFolder.absolutePath)
            val stateData = SaveState(byteArrayOf(5, 6, 7), SaveState.Metadata(diskIndex = 1))

            statesManager.setSlotSave(game, stateData, CoreID.MGBA, 0)

            // Custom folder has state and metadata in states/<console>/<core>/
            val customCoreDir = File(customFolder, "states/GBA/${CoreID.MGBA.coreName}")
            val customStateFile = File(customCoreDir, "${game.fileName}.slot1")
            val customMetaFile = File(customCoreDir, "${game.fileName}.slot1.metadata")
            assertTrue(customStateFile.exists())
            assertTrue(customMetaFile.exists())

            // Local internal storage must NOT have the state (zero replication)
            val localCoreDir = File(directories.getStatesDirectory(), CoreID.MGBA.coreName)
            val localStateFile = File(localCoreDir, "${game.fileName}.slot1")
            val localMetaFile = File(localCoreDir, "${game.fileName}.slot1.metadata")
            assertFalse(localStateFile.exists())
            assertFalse(localMetaFile.exists())

            // Reading does not create local cache file
            val loaded = statesManager.getSlotSave(game, CoreID.MGBA, 0)
            assertNotNull(loaded)
            assertArrayEquals(stateData.state, loaded!!.state)
            assertEquals(1, loaded.metadata.diskIndex)
            assertFalse(localStateFile.exists())
        }
    }

    @Test
    fun statesManagerDiscoversCustomSlots() {
        runBlocking {
            setCustomFolderPref(customFolder.absolutePath)
            val customConsoleCoreDir = File(customFolder, "states/GBA/${CoreID.MGBA.coreName}")
            customConsoleCoreDir.mkdirs()
            val legacyCoreDir = File(customFolder, "states/${CoreID.MGBA.coreName}")
            legacyCoreDir.mkdirs()

            // Put slot 10 in console-scoped directory and slot 12 in legacy core directory
            File(customConsoleCoreDir, "${game.fileName}.slot10").writeBytes(byteArrayOf(1, 2, 3))
            File(legacyCoreDir, "${game.fileName}.slot12").writeBytes(byteArrayOf(4, 5, 6))

            val slots = statesManager.getSavedSlotsInfo(game, CoreID.MGBA)
            val slotIndices = slots.map { it.index }
            assertTrue(slotIndices.contains(9)) // 0-indexed index 9 is slot 10
            assertTrue(slotIndices.contains(11)) // 0-indexed index 11 is slot 12
            assertTrue(slots.first { it.index == 9 }.saveInfo.exists)
            assertTrue(slots.first { it.index == 11 }.saveInfo.exists)
        }
    }

    @Test
    fun legacyCustomFolderSavesAndStatesRemainReadableAndUpgradeOnWrite() =
        runBlocking {
            setCustomFolderPref(customFolder.absolutePath)

            // Existing v3.1.0 layout in custom folder
            val legacySram = byteArrayOf(11, 22, 33)
            val legacySaveFile = File(customFolder, "saves/Pokemon - Fire Red (USA).srm")
            legacySaveFile.parentFile!!.mkdirs()
            legacySaveFile.writeBytes(legacySram)

            val legacyCoreDir = File(customFolder, "states/${CoreID.MGBA.coreName}")
            legacyCoreDir.mkdirs()
            val legacySlotFile = File(legacyCoreDir, "${game.fileName}.slot1")
            val legacySlotMeta = File(legacyCoreDir, "${game.fileName}.slot1.metadata")
            legacySlotFile.writeBytes(byteArrayOf(7, 8, 9))
            legacySlotMeta.writeText("""{"diskIndex":2,"version":0}""")

            val legacyAutoFile = File(legacyCoreDir, "${game.fileName}.state")
            val legacyAutoMeta = File(legacyCoreDir, "${game.fileName}.state.metadata")
            legacyAutoFile.writeBytes(byteArrayOf(4, 5, 6))
            legacyAutoMeta.writeText("""{"diskIndex":1,"version":0}""")

            // Existing saves & states load cleanly
            assertArrayEquals(legacySram, savesManager.getSaveRAM(game, systemCoreConfig))
            assertTrue(savesManager.getSaveRAMInfo(game).exists)

            val loadedSlot = statesManager.getSlotSave(game, CoreID.MGBA, 0)!!
            assertArrayEquals(byteArrayOf(7, 8, 9), loadedSlot.state)
            assertEquals(2, loadedSlot.metadata.diskIndex)

            val loadedAuto = statesManager.getAutoSave(game, CoreID.MGBA)!!
            assertArrayEquals(byteArrayOf(4, 5, 6), loadedAuto.state)
            assertEquals(1, loadedAuto.metadata.diskIndex)
            assertTrue(statesManager.getAutoSaveInfo(game, CoreID.MGBA).exists)

            // Writing upgrades to console-scoped paths and removes unambiguous legacy duplicates
            val newSram = byteArrayOf(99, 100)
            savesManager.setSaveRAM(game, newSram)
            assertTrue(File(customFolder, "saves/GBA/Pokemon - Fire Red (USA).srm").exists())
            assertFalse(legacySaveFile.exists())
            assertArrayEquals(newSram, savesManager.getSaveRAM(game, systemCoreConfig))

            val newSlot = SaveState(byteArrayOf(55, 66), SaveState.Metadata(diskIndex = 3))
            statesManager.setSlotSave(game, newSlot, CoreID.MGBA, 0)
            assertTrue(File(customFolder, "states/GBA/${CoreID.MGBA.coreName}/${game.fileName}.slot1").exists())
            assertFalse(legacySlotFile.exists())
            assertFalse(legacySlotMeta.exists())
            assertArrayEquals(newSlot.state, statesManager.getSlotSave(game, CoreID.MGBA, 0)!!.state)

            val newAuto = SaveState(byteArrayOf(77, 88), SaveState.Metadata(diskIndex = 0))
            statesManager.setAutoSave(game, CoreID.MGBA, newAuto)
            assertTrue(File(customFolder, "states/GBA/${CoreID.MGBA.coreName}/${game.fileName}.state").exists())
            assertFalse(legacyAutoFile.exists())
            assertFalse(legacyAutoMeta.exists())
            assertArrayEquals(newAuto.state, statesManager.getAutoSave(game, CoreID.MGBA)!!.state)
        }

    @Test
    fun legacySavFilesInRootOrConsoleFolderRemainReadable() =
        runBlocking {
            setCustomFolderPref(customFolder.absolutePath)

            // Legacy .sav in saves/
            val legacySav =
                File(customFolder, "saves/Pokemon - Fire Red (USA).sav").apply {
                    parentFile!!.mkdirs()
                    writeBytes(byteArrayOf(1, 2, 3))
                }
            assertArrayEquals(byteArrayOf(1, 2, 3), savesManager.getSaveRAM(game, systemCoreConfig))

            // Console-scoped .sav (e.g. copied from another emulator into saves/GBA/) takes priority
            val consoleSav =
                File(customFolder, "saves/GBA/Pokemon - Fire Red (USA).sav").apply {
                    parentFile!!.mkdirs()
                    writeBytes(byteArrayOf(9, 8, 7))
                }
            assertArrayEquals(byteArrayOf(9, 8, 7), savesManager.getSaveRAM(game, systemCoreConfig))
            assertTrue(legacySav.exists())
            assertTrue(consoleSav.exists())
        }

    @Test
    fun legacySharedSramNotDeletedWhenMultipleSystemsShareStem() =
        runBlocking {
            setCustomFolderPref(customFolder.absolutePath)
            val gbGame =
                Game(
                    id = 2,
                    fileName = "Tetris (World).gb",
                    fileUri = "content://games/2",
                    title = "Tetris GB",
                    systemId = "gb",
                    developer = null,
                    coverFrontUrl = null,
                    lastIndexedAt = 0,
                )
            val nesGame =
                Game(
                    id = 3,
                    fileName = "Tetris (World).nes",
                    fileUri = "content://games/3",
                    title = "Tetris NES",
                    systemId = "nes",
                    developer = null,
                    coverFrontUrl = null,
                    lastIndexedAt = 0,
                )
            db.gameDao().insert(listOf(gbGame, nesGame))

            val legacyShared =
                File(customFolder, "saves/Tetris (World).srm").apply {
                    parentFile!!.mkdirs()
                    writeBytes(byteArrayOf(12, 34))
                }

            // Saving GB writes to saves/GB/ and keeps legacy shared file intact for NES
            savesManager.setSaveRAM(gbGame, byteArrayOf(56, 78))
            assertTrue(File(customFolder, "saves/GB/Tetris (World).srm").exists())
            assertTrue(legacyShared.exists())
            assertArrayEquals(
                byteArrayOf(12, 34),
                savesManager.getSaveRAM(nesGame, SystemCoreConfig(CoreID.FCEUMM, hashMapOf())),
            )
        }

    @Test
    fun sharedCoreSystemsStayIsolatedInCustomFolder() =
        runBlocking {
            setCustomFolderPref(customFolder.absolutePath)
            val gcGame =
                Game(
                    id = 4,
                    fileName = "SharedTitle.iso",
                    fileUri = "content://games/4",
                    title = "Shared GC",
                    systemId = "gamecube",
                    developer = null,
                    coverFrontUrl = null,
                    lastIndexedAt = 0,
                )
            val wiiGame =
                Game(
                    id = 5,
                    fileName = "SharedTitle.iso",
                    fileUri = "content://games/5",
                    title = "Shared Wii",
                    systemId = "wii",
                    developer = null,
                    coverFrontUrl = null,
                    lastIndexedAt = 0,
                )
            val gcConfig = SystemCoreConfig(CoreID.DOLPHIN, hashMapOf(), statesSupported = true)

            // Ambiguous legacy state should NOT be deleted when GameCube saves
            val legacyDolphinState =
                File(customFolder, "states/dolphin/SharedTitle.iso.slot1").apply {
                    parentFile!!.mkdirs()
                    writeBytes(byteArrayOf(1))
                }

            savesManager.setSaveRAM(gcGame, byteArrayOf(10))
            savesManager.setSaveRAM(wiiGame, byteArrayOf(20))
            statesManager.setSlotSave(gcGame, SaveState(byteArrayOf(30), SaveState.Metadata()), CoreID.DOLPHIN, 0)
            statesManager.setSlotSave(wiiGame, SaveState(byteArrayOf(40), SaveState.Metadata()), CoreID.DOLPHIN, 0)

            assertTrue(File(customFolder, "saves/GameCube/SharedTitle.srm").exists())
            assertTrue(File(customFolder, "saves/Wii/SharedTitle.srm").exists())
            assertTrue(File(customFolder, "states/GameCube/dolphin/SharedTitle.iso.slot1").exists())
            assertTrue(File(customFolder, "states/Wii/dolphin/SharedTitle.iso.slot1").exists())
            assertTrue(legacyDolphinState.exists())

            assertArrayEquals(byteArrayOf(10), savesManager.getSaveRAM(gcGame, gcConfig))
            assertArrayEquals(byteArrayOf(20), savesManager.getSaveRAM(wiiGame, gcConfig))
            assertArrayEquals(byteArrayOf(30), statesManager.getSlotSave(gcGame, CoreID.DOLPHIN, 0)!!.state)
            assertArrayEquals(byteArrayOf(40), statesManager.getSlotSave(wiiGame, CoreID.DOLPHIN, 0)!!.state)
        }

    @Test
    fun offlineStashNestedPathsDoNotLeakAcrossConsolesOrParents() {
        val blockedTarget = File(temporary.root, "unwritable_share").apply { writeText("file") }
        setCustomFolderPref(blockedTarget.absolutePath)

        assertTrue(customStorage.writeSave("states/Wii/dolphin/Shared.iso.slot5", byteArrayOf(9, 9)))

        // Direct parent listing finds it; unrelated parent or different console does not
        assertEquals(listOf("Shared.iso.slot5"), customStorage.listSaveFiles("states/Wii/dolphin"))
        assertEquals(emptyList<String>(), customStorage.listSaveFiles("states/GameCube/dolphin"))
        assertEquals(emptyList<String>(), customStorage.listSaveFiles("states"))
        assertEquals(emptyList<String>(), customStorage.listSaveFiles())
    }

    @Test
    fun statesPreviewManagerSplitsByConsoleAndAvoidsStaleLegacyPreviews() =
        runBlocking {
            setCustomFolderPref(customFolder.absolutePath)

            // Legacy state + legacy preview
            val legacyState =
                File(customFolder, "states/${CoreID.MGBA.coreName}/${game.fileName}.slot1").apply {
                    parentFile!!.mkdirs()
                    writeBytes(byteArrayOf(1, 2, 3))
                }
            val legacyPreview =
                File(
                    customFolder,
                    "state-previews/${CoreID.MGBA.coreName}/${game.fileName}.slot1.jpg",
                ).apply {
                    parentFile!!.mkdirs()
                    writeBytes(sampleJpegBytes())
                }
            assertTrue(legacyState.exists())
            assertNotNull(previewManager.getPreviewForSlot(game, CoreID.MGBA, 0, 32))

            // Saving state to console-scoped folder without writing new preview must NOT return stale legacy preview
            statesManager.setSlotSave(game, SaveState(byteArrayOf(4, 5), SaveState.Metadata()), CoreID.MGBA, 0)
            assertNull(previewManager.getPreviewForSlot(game, CoreID.MGBA, 0, 32))

            // Writing new preview stores it under state-previews/GBA/mgba/ and removes unambiguous legacy preview
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            previewManager.setPreviewForSlot(game, bitmap, CoreID.MGBA, 0)
            bitmap.recycle()

            val consolePreview =
                File(
                    customFolder,
                    "state-previews/GBA/${CoreID.MGBA.coreName}/${game.fileName}.slot1.jpg",
                )
            assertTrue(consolePreview.exists())
            assertFalse(legacyPreview.exists())
            assertNotNull(previewManager.getPreviewForSlot(game, CoreID.MGBA, 0, 32))
        }

    @Test
    fun offlineFallbackAndDeferredSync() {
        // Point to an unreachable destination (a file that cannot act as a directory)
        val blockedTarget = File(temporary.root, "unwritable_network_share").apply { writeText("file") }
        setCustomFolderPref(blockedTarget.absolutePath)

        val saveBytes = byteArrayOf(42, 43, 44)
        // Writing falls back to local stash
        val writeResult = customStorage.writeSave("saves/GBA/offline.srm", saveBytes)
        assertTrue(writeResult)
        assertTrue(customStorage.isStashedLocally("saves/GBA/offline.srm"))

        // Stashed save can be read locally while offline
        val readBytes = customStorage.readSave("saves/GBA/offline.srm")
        assertNotNull(readBytes)
        assertArrayEquals(saveBytes, readBytes)

        // Now network location is reachable (switch to a valid folder)
        val reachableFolder = temporary.newFolder("online_network_share")
        setCustomFolderPref(reachableFolder.absolutePath)

        // Sync stashed saves to network location
        val syncSuccess = customStorage.syncOfflineStash()
        assertTrue(syncSuccess)

        // File is now in network folder, and local stash copy is cleaned up
        val syncedFile = File(reachableFolder, "saves/GBA/offline.srm")
        assertTrue(syncedFile.exists())
        assertArrayEquals(saveBytes, syncedFile.readBytes())
        assertFalse(customStorage.isStashedLocally("saves/GBA/offline.srm"))
        assertFalse(customStorage.getInternalFile("saves/GBA/offline.srm").exists())
    }

    @Test
    fun migrationFromInternalToCustomMovesFilesIntoConsoleFoldersAndDeletesInternal() =
        runBlocking {
            // Save in internal storage before migration
            val saveBytes = byteArrayOf(1, 2, 3)
            val stateBytes = byteArrayOf(4, 5, 6)
            savesManager.setSaveRAM(game, saveBytes)
            statesManager.setSlotSave(game, SaveState(stateBytes, SaveState.Metadata()), CoreID.MGBA, 0)
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            previewManager.setPreviewForSlot(game, bitmap, CoreID.MGBA, 0)
            bitmap.recycle()

            val localSave = File(directories.getSavesDirectory(), "Pokemon - Fire Red (USA).srm")
            val localState = File(directories.getStatesDirectory(), "${CoreID.MGBA.coreName}/${game.fileName}.slot1")
            val localPreview =
                File(directories.getStatesPreviewDirectory(), "${CoreID.MGBA.coreName}/${game.fileName}.slot1.jpg")
            assertTrue(localSave.exists())
            assertTrue(localState.exists())
            assertTrue(localPreview.exists())

            // Migrate to custom folder
            val result = customStorage.migrateStorage(null, customFolder.absolutePath)
            assertTrue(result is CustomSaveStorage.MigrationResult.Success)

            // Custom folder now has all files organized by console
            val customSave = File(customFolder, "saves/GBA/Pokemon - Fire Red (USA).srm")
            val customState = File(customFolder, "states/GBA/${CoreID.MGBA.coreName}/${game.fileName}.slot1")
            val customPreview =
                File(customFolder, "state-previews/GBA/${CoreID.MGBA.coreName}/${game.fileName}.slot1.jpg")
            assertTrue(customSave.exists())
            assertTrue(customState.exists())
            assertTrue(customPreview.exists())
            assertArrayEquals(saveBytes, customSave.readBytes())

            // Internal files were deleted
            assertFalse(localSave.exists())
            assertFalse(localState.exists())
            assertFalse(localPreview.exists())

            // Preference is set to custom folder
            assertEquals(customFolder.absolutePath, customStorage.getCustomSavesFolderUriString())
        }

    @Test
    fun migrationFromCustomToInternalMovesFilesAndDeletesCustom() =
        runBlocking {
            // Setup saves in custom folder
            setCustomFolderPref(customFolder.absolutePath)
            val saveBytes = byteArrayOf(7, 8, 9)
            val stateBytes = byteArrayOf(10, 11, 12)
            savesManager.setSaveRAM(game, saveBytes)
            statesManager.setSlotSave(game, SaveState(stateBytes, SaveState.Metadata()), CoreID.MGBA, 0)

            val customSave = File(customFolder, "saves/GBA/Pokemon - Fire Red (USA).srm")
            val customState = File(customFolder, "states/GBA/${CoreID.MGBA.coreName}/${game.fileName}.slot1")
            assertTrue(customSave.exists())
            assertTrue(customState.exists())

            // Migrate back to internal storage (toUriString = null)
            val result = customStorage.migrateStorage(customFolder.absolutePath, null)
            assertTrue(result is CustomSaveStorage.MigrationResult.Success)

            // Internal storage now has all files
            val localSave = File(directories.getSavesDirectory(), "Pokemon - Fire Red (USA).srm")
            val localState = File(directories.getStatesDirectory(), "${CoreID.MGBA.coreName}/${game.fileName}.slot1")
            assertTrue(localSave.exists())
            assertTrue(localState.exists())
            assertArrayEquals(saveBytes, localSave.readBytes())

            // Custom folder files were deleted
            assertFalse(customSave.exists())
            assertFalse(customState.exists())

            // Preference is cleared
            assertNull(customStorage.getCustomSavesFolderUriString())
        }

    @Test
    fun migrationFromCustomToInternalHandlesDuplicatesAndDistinctConsoleCollisions() =
        runBlocking {
            // Console-scoped save AND stale legacy save in custom folder: console-scoped must win
            val newGbaBytes = byteArrayOf(100, 101)
            val staleGbaBytes = byteArrayOf(1, 2)
            File(customFolder, "saves/GBA/Pokemon - Fire Red (USA).srm").apply {
                parentFile!!.mkdirs()
                writeBytes(newGbaBytes)
            }
            File(customFolder, "saves/Pokemon - Fire Red (USA).srm").apply {
                parentFile!!.mkdirs()
                writeBytes(staleGbaBytes)
            }

            // Two distinct consoles sharing the same .srm filename
            val gbBytes = byteArrayOf(11, 12)
            val nesBytes = byteArrayOf(21, 22)
            File(customFolder, "saves/GB/Tetris.srm").apply {
                parentFile!!.mkdirs()
                writeBytes(gbBytes)
            }
            File(customFolder, "saves/NES/Tetris.srm").apply {
                parentFile!!.mkdirs()
                writeBytes(nesBytes)
            }

            setCustomFolderPref(customFolder.absolutePath)
            val result = customStorage.migrateStorage(customFolder.absolutePath, null)
            assertTrue(result is CustomSaveStorage.MigrationResult.Success)

            // Newer console-scoped Pokemon save won over stale legacy copy
            assertArrayEquals(
                newGbaBytes,
                File(directories.getSavesDirectory(), "Pokemon - Fire Red (USA).srm").readBytes(),
            )

            // Both Tetris saves are preserved and readable by their respective consoles in internal storage
            val gbGame = game.copy(id = 10, fileName = "Tetris.gb", systemId = "gb")
            val nesGame = game.copy(id = 11, fileName = "Tetris.nes", systemId = "nes")
            assertArrayEquals(
                gbBytes,
                savesManager.getSaveRAM(gbGame, SystemCoreConfig(CoreID.GAMBATTE, hashMapOf())),
            )
            assertArrayEquals(
                nesBytes,
                savesManager.getSaveRAM(nesGame, SystemCoreConfig(CoreID.FCEUMM, hashMapOf())),
            )
        }

    @Test
    fun migrationFromCustomToInternalPreservesLocallyStashedSamePathFile() =
        runBlocking {
            // Simulate an offline-stashed legacy save whose internal destination is identical to its stash file
            val blockedTarget = File(temporary.root, "offline_target").apply { writeText("file") }
            setCustomFolderPref(blockedTarget.absolutePath)
            val stashedBytes = byteArrayOf(77, 88, 99)
            assertTrue(customStorage.writeSave("saves/Pokemon - Fire Red (USA).srm", stashedBytes))
            assertTrue(customStorage.isStashedLocally("saves/Pokemon - Fire Red (USA).srm"))

            // Now source becomes reachable again (empty folder) and user resets back to internal storage
            val reachableCustom = temporary.newFolder("reconnected_custom")
            setCustomFolderPref(reachableCustom.absolutePath)
            val result = customStorage.migrateStorage(reachableCustom.absolutePath, null)
            assertTrue(result is CustomSaveStorage.MigrationResult.Success)

            val internalFile = File(directories.getSavesDirectory(), "Pokemon - Fire Red (USA).srm")
            assertTrue(internalFile.exists())
            assertArrayEquals(stashedBytes, internalFile.readBytes())
            assertFalse(customStorage.isStashedLocally("saves/Pokemon - Fire Red (USA).srm"))
        }

    @Test
    fun migrationFromCustomToCustomUpgradesLegacyLayoutIntoConsoleFolders() =
        runBlocking {
            val legacySram = byteArrayOf(31, 32)
            File(customFolder, "saves/Pokemon - Fire Red (USA).srm").apply {
                parentFile!!.mkdirs()
                writeBytes(legacySram)
            }
            File(customFolder, "states/${CoreID.MGBA.coreName}/${game.fileName}.slot1").apply {
                parentFile!!.mkdirs()
                writeBytes(byteArrayOf(41, 42))
            }
            File(customFolder, "state-previews/${CoreID.MGBA.coreName}/${game.fileName}.slot1.jpg").apply {
                parentFile!!.mkdirs()
                writeBytes(sampleJpegBytes())
            }

            setCustomFolderPref(customFolder.absolutePath)
            val newCustomFolder = temporary.newFolder("new_custom_saves")
            val result = customStorage.migrateStorage(customFolder.absolutePath, newCustomFolder.absolutePath)
            assertTrue(result is CustomSaveStorage.MigrationResult.Success)

            assertTrue(File(newCustomFolder, "saves/GBA/Pokemon - Fire Red (USA).srm").exists())
            assertTrue(File(newCustomFolder, "states/GBA/${CoreID.MGBA.coreName}/${game.fileName}.slot1").exists())
            assertTrue(
                File(newCustomFolder, "state-previews/GBA/${CoreID.MGBA.coreName}/${game.fileName}.slot1.jpg").exists(),
            )
            assertFalse(File(customFolder, "saves/Pokemon - Fire Red (USA).srm").exists())
            assertEquals(newCustomFolder.absolutePath, customStorage.getCustomSavesFolderUriString())
        }

    @Test
    fun savesCoherencyEngineWorksAcrossLegacyAndConsoleScopedSaves() =
        runBlocking {
            setCustomFolderPref(customFolder.absolutePath)
            val coherency = SavesCoherencyEngine(savesManager, statesManager)

            val legacyAuto =
                File(customFolder, "states/${CoreID.MGBA.coreName}/${game.fileName}.state").apply {
                    parentFile!!.mkdirs()
                    writeBytes(byteArrayOf(1, 2, 3))
                    setLastModified(100_000L)
                }
            val consoleSram =
                File(customFolder, "saves/GBA/Pokemon - Fire Red (USA).srm").apply {
                    parentFile!!.mkdirs()
                    writeBytes(byteArrayOf(4, 5, 6))
                    setLastModified(200_000L)
                }
            assertTrue(legacyAuto.exists())
            assertTrue(consoleSram.exists())
            assertTrue(coherency.shouldDiscardAutoSaveState(game, CoreID.MGBA))

            // Newer console-scoped autosave supersedes older SRAM
            val consoleAuto =
                File(customFolder, "states/GBA/${CoreID.MGBA.coreName}/${game.fileName}.state").apply {
                    parentFile!!.mkdirs()
                    writeBytes(byteArrayOf(7, 8, 9))
                    setLastModified(300_000L)
                }
            assertTrue(consoleAuto.exists())
            assertFalse(coherency.shouldDiscardAutoSaveState(game, CoreID.MGBA))
        }

    @Test
    fun migrationFailurePreservesSourceAndAbortsSafely() =
        runBlocking {
            // Setup save in internal storage
            val saveBytes = byteArrayOf(99, 88)
            savesManager.setSaveRAM(game, saveBytes)
            val localSave = File(directories.getSavesDirectory(), "Pokemon - Fire Red (USA).srm")
            assertTrue(localSave.exists())

            // Attempt migrating to unwritable destination
            val blocked = File(temporary.root, "blocked_dest").apply { writeText("not a directory") }
            val result = customStorage.migrateStorage(null, blocked.absolutePath)

            assertTrue(result is CustomSaveStorage.MigrationResult.Failure)

            // Source files are untouched
            assertTrue(localSave.exists())
            assertArrayEquals(saveBytes, localSave.readBytes())

            // Preference was NOT set
            assertNull(customStorage.getCustomSavesFolderUriString())
        }

    private fun sampleJpegBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private fun setCustomFolderPref(path: String) {
        val key =
            runCatching { context.getString(R.string.pref_key_custom_saves_folder) }
                .getOrDefault(CustomSaveStorage.PREF_KEY_CUSTOM_SAVES_FOLDER)
        SharedPreferencesHelper
            .getLegacySharedPreferences(context)
            .edit()
            .putString(key, path)
            .commit()
    }
}
