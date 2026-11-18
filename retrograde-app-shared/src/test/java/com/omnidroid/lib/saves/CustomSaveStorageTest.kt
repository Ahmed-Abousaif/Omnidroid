package com.omnidroid.lib.saves

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.omnidroid.lib.R
import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import com.omnidroid.lib.storage.DirectoriesManager
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
class CustomSaveStorageTest {
    @get:Rule val temporary = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var directories: DirectoriesManager
    private lateinit var customStorage: CustomSaveStorage
    private lateinit var savesManager: SavesManager
    private lateinit var statesManager: StatesManager
    private lateinit var customFolder: File

    private val game =
        Game(
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

        customFolder = temporary.newFolder("custom_saves")
        directories = DirectoriesManager(context)
        customStorage = CustomSaveStorage(context, directories)
        savesManager = SavesManager(directories, customStorage)
        statesManager = StatesManager(directories, customStorage)
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

            // Custom folder has the save
            val customFile = File(customFolder, "saves/Pokemon - Fire Red (USA).srm")
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

            // Custom folder has state and metadata in states/<core>/
            val customCoreDir = File(customFolder, "states/${CoreID.MGBA.coreName}")
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
            val customCoreDir = File(customFolder, "states/${CoreID.MGBA.coreName}")
            customCoreDir.mkdirs()

            // Put slot 10 in custom directory
            File(customCoreDir, "${game.fileName}.slot10").writeBytes(byteArrayOf(1, 2, 3))

            val slots = statesManager.getSavedSlotsInfo(game, CoreID.MGBA)
            val slotIndices = slots.map { it.index }
            assertTrue(slotIndices.contains(9)) // 0-indexed index 9 is slot 10
            val slot10Info = slots.first { it.index == 9 }
            assertTrue(slot10Info.saveInfo.exists)
        }
    }

    @Test
    fun offlineFallbackAndDeferredSync() {
        // Point to an unreachable destination (a file that cannot act as a directory)
        val blockedTarget = File(temporary.root, "unwritable_network_share").apply { writeText("file") }
        setCustomFolderPref(blockedTarget.absolutePath)

        val saveBytes = byteArrayOf(42, 43, 44)
        // Writing falls back to local stash
        val writeResult = customStorage.writeSave("saves/offline.srm", saveBytes)
        assertTrue(writeResult)
        assertTrue(customStorage.isStashedLocally("saves/offline.srm"))

        // Stashed save can be read locally while offline
        val readBytes = customStorage.readSave("saves/offline.srm")
        assertNotNull(readBytes)
        assertArrayEquals(saveBytes, readBytes)

        // Now network location is reachable (switch to a valid folder)
        val reachableFolder = temporary.newFolder("online_network_share")
        setCustomFolderPref(reachableFolder.absolutePath)

        // Sync stashed saves to network location
        val syncSuccess = customStorage.syncOfflineStash()
        assertTrue(syncSuccess)

        // File is now in network folder, and local stash copy is cleaned up
        val syncedFile = File(reachableFolder, "saves/offline.srm")
        assertTrue(syncedFile.exists())
        assertArrayEquals(saveBytes, syncedFile.readBytes())
        assertFalse(customStorage.isStashedLocally("saves/offline.srm"))
        assertFalse(customStorage.getInternalFile("saves/offline.srm").exists())
    }

    @Test
    fun migrationFromInternalToCustomMovesFilesAndDeletesInternal() =
        runBlocking {
            // Save in internal storage before migration
            val saveBytes = byteArrayOf(1, 2, 3)
            val stateBytes = byteArrayOf(4, 5, 6)
            savesManager.setSaveRAM(game, saveBytes)
            statesManager.setSlotSave(game, SaveState(stateBytes, SaveState.Metadata()), CoreID.MGBA, 0)

            val localSave = File(directories.getSavesDirectory(), "Pokemon - Fire Red (USA).srm")
            val localState = File(directories.getStatesDirectory(), "${CoreID.MGBA.coreName}/${game.fileName}.slot1")
            assertTrue(localSave.exists())
            assertTrue(localState.exists())

            // Migrate to custom folder
            val result = customStorage.migrateStorage(null, customFolder.absolutePath)
            assertTrue(result is CustomSaveStorage.MigrationResult.Success)

            // Custom folder now has all files
            val customSave = File(customFolder, "saves/Pokemon - Fire Red (USA).srm")
            val customState = File(customFolder, "states/${CoreID.MGBA.coreName}/${game.fileName}.slot1")
            assertTrue(customSave.exists())
            assertTrue(customState.exists())
            assertArrayEquals(saveBytes, customSave.readBytes())

            // Internal files were deleted
            assertFalse(localSave.exists())
            assertFalse(localState.exists())

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

            val customSave = File(customFolder, "saves/Pokemon - Fire Red (USA).srm")
            val customState = File(customFolder, "states/${CoreID.MGBA.coreName}/${game.fileName}.slot1")
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
