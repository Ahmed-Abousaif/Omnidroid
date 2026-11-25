package com.omnidroid.lib.library

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.omnidroid.lib.bios.BiosManager
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.DataFile
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.library.metadata.ArcadeResolution
import com.omnidroid.lib.library.metadata.GameMetadata
import com.omnidroid.lib.library.metadata.GameMetadataProvider
import com.omnidroid.lib.library.scan.ByteArraySource
import com.omnidroid.lib.library.scan.MetadataKey
import com.omnidroid.lib.library.scan.PartialContentHasher
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import com.omnidroid.lib.storage.BaseStorageFile
import com.omnidroid.lib.storage.DirectoriesManager
import com.omnidroid.lib.storage.RomFiles
import com.omnidroid.lib.storage.StorageFile
import com.omnidroid.lib.storage.StorageListOptions
import com.omnidroid.lib.storage.StorageProvider
import com.omnidroid.lib.storage.StorageProviderRegistry
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OmnidroidLibraryBiosTest {
    private lateinit var context: Context
    private lateinit var database: RetrogradeDatabase
    private lateinit var directories: DirectoriesManager
    private lateinit var biosManager: BiosManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room.inMemoryDatabaseBuilder(context, RetrogradeDatabase::class.java).allowMainThreadQueries().build()
        directories = DirectoriesManager(context)
        biosManager = BiosManager(directories)
        clearBios()
    }

    @After
    fun tearDown() {
        database.close()
        clearBios()
    }

    @Test
    fun canonicalRegionalBiosAreInstalledBeforeGenesisHeaderDetection() =
        runBlocking {
            val files = listOf('E', 'J', 'U').associate { "bios_CD_$it.bin" to biosBytes(it) }
            val library = library(files)

            library.indexLibrary()
            library.indexLibrary()

            assertEquals(0, database.gameDao().countAll())
            files.forEach { (name, bytes) ->
                assertArrayEquals(bytes, File(directories.getSystemDirectory(), name).readBytes())
            }
            assertTrue(biosManager.getBiosInfoAsync().detected.count { it.systemID == SystemID.SEGACD } == 3)
            val config =
                SystemCoreConfig(
                    CoreID.GENESIS_PLUS_GX,
                    controllerConfigs = hashMapOf(),
                    regionalBIOSFiles = mapOf("Japan" to "bios_CD_J.bin"),
                )
            assertTrue(biosManager.getMissingBiosFiles(config, game("Final Fight CD (Japan) (Rev A).chd")).isEmpty())
        }

    @Test
    fun differentlyCasedNameIsInstalledWithCoreExpectedCasing() =
        runBlocking {
            val bytes = biosBytes('J')

            library(mapOf("BIOS_cd_j.BIN" to bytes)).indexLibrary()

            assertArrayEquals(bytes, File(directories.getSystemDirectory(), "bios_CD_J.bin").readBytes())
            assertEquals(0, database.gameDao().countAll())
        }

    @Test
    fun unchangedAutomaticBiosGameIsRemovedOnRescan() =
        runBlocking {
            val name = "bios_CD_J.bin"
            database.gameDao().insert(listOf(game(name)))

            library(mapOf(name to biosBytes('J'))).indexLibrary()

            assertEquals(0, database.gameDao().countAll())
            assertTrue(File(directories.getSystemDirectory(), name).isFile)
        }

    @Test
    fun modifiedAutomaticBiosGameIsRemovedOnRescan() =
        runBlocking {
            val name = "bios_CD_J.bin"
            database.gameDao().insert(listOf(game(name).copy(fileSize = 1)))

            library(mapOf(name to biosBytes('J'))).indexLibrary()

            assertEquals(0, database.gameDao().countAll())
            assertTrue(File(directories.getSystemDirectory(), name).isFile)
        }

    @Test
    fun renamedAutomaticBiosGameIsRemovedOnRescan() =
        runBlocking {
            val bytes = biosBytes('J')
            val hash = PartialContentHasher.hash(ByteArraySource(bytes))
            database.gameDao().insert(listOf(game("old-name.bin").copy(partialHash = hash)))

            library(mapOf("bios_CD_J.bin" to bytes)).indexLibrary()

            assertEquals(0, database.gameDao().countAll())
            assertTrue(File(directories.getSystemDirectory(), "bios_CD_J.bin").isFile)
        }

    @Test
    fun biosWithGenesisHeaderIsRecognizedByChecksumUnderAnotherName() =
        runBlocking {
            library(mapOf("Mega CD boot.bin" to biosBytes('J'))).indexLibrary()

            assertEquals(0, database.gameDao().countAll())
            assertTrue(File(directories.getSystemDirectory(), "bios_CD_J.bin").isFile)
        }

    @Test
    fun biosNameAloneDoesNotMakeAnUnrecognizedChecksumIntoFirmware() =
        runBlocking {
            val bytes = biosBytes('J').also { it[0] = 1 }

            library(mapOf("bios_CD_J.bin" to bytes)).indexLibrary()

            assertFalse(File(directories.getSystemDirectory(), "bios_CD_J.bin").exists())
            assertEquals(1, database.gameDao().countAll())
        }

    @Test
    fun cachedSubtreeDoesNotPrunePreviouslyDetectedBios() =
        runBlocking {
            val provider = BiosStorageProvider(mapOf("bios_CD_J.bin" to biosBytes('J')))
            val library = library(provider)
            library.indexLibrary()
            val installed = File(directories.getSystemDirectory(), "bios_CD_J.bin")
            assertTrue(installed.isFile)
            assertTrue(installed.setLastModified(1_000))
            provider.skipSubtree = true

            library.indexLibrary()

            assertTrue(installed.isFile)
            assertTrue(biosManager.getBiosInfoAsync().detected.any { it.libretroFileName == installed.name })
        }

    @Test
    fun manuallyMatchedEntryIsPreservedOnRescan() =
        runBlocking {
            val manual = game("bios_CD_J.bin").copy(isManualMatch = true)
            database.gameDao().insert(listOf(manual))

            library(mapOf(manual.fileName to biosBytes('J'))).indexLibrary()

            assertEquals(1, database.gameDao().countAll())
            assertTrue(
                database
                    .gameDao()
                    .selectAllBlocking()
                    .single()
                    .isManualMatch,
            )
        }

    private fun library(files: Map<String, ByteArray>) = library(BiosStorageProvider(files))

    private fun library(provider: BiosStorageProvider): OmnidroidLibrary {
        val registry = StorageProviderRegistry(context, setOf(provider))
        val prefs = SharedPreferencesHelper.getLegacySharedPreferences(context)
        prefs.edit().clear().commit()
        return OmnidroidLibrary(
            database,
            { registry },
            { EmptyMetadata() },
            biosManager,
            SliceStampStore(prefs),
            directories,
        )
    }

    private fun game(name: String) =
        Game(
            fileName = name,
            fileUri = "content://bios-test/$name",
            title = "Previously detected Genesis game",
            systemId = SystemID.GENESIS.dbname,
            developer = null,
            coverFrontUrl = null,
            lastIndexedAt = 100,
            fileSize = 128 * 1024L,
            fileLastModified = 500,
        )

    private fun clearBios() {
        listOf('E', 'J', 'U').forEach { File(directories.getSystemDirectory(), "bios_CD_$it.bin").delete() }
    }

    private fun biosBytes(region: Char): ByteArray {
        // Synthetic data, not firmware. The suffix gives each fixture the catalog's CRC32,
        // while the Sega header reproduces the competing Genesis cartridge detection path.
        val suffix =
            when (region) {
                'E' -> "23cebe4d"
                'J' -> "855da59c"
                else -> "f4b24d2d"
            }.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return ByteArray(128 * 1024).also {
            "SEGA MEGA DRIVE ".toByteArray().copyInto(it, 0x100)
            suffix.copyInto(it, it.size - suffix.size)
        }
    }

    private class BiosStorageProvider(
        private val bytes: Map<String, ByteArray>,
    ) : StorageProvider {
        var skipSubtree = false
        override val id = "bios-test"
        override val name = "BIOS test files"
        override val uriSchemes = listOf("content")
        override val prefsFragmentClass = null
        override val enabledByDefault = true

        override fun listBaseStorageFiles(options: StorageListOptions) =
            flowOf(
                (if (skipSubtree) emptyMap() else bytes).map { (name, content) ->
                    BaseStorageFile(
                        name,
                        content.size.toLong(),
                        Uri.parse("content://bios-test/$name"),
                        lastModified = 500,
                    )
                },
            )

        override fun lastSkippedDocumentIds(): Set<String> = if (skipSubtree) setOf("bios-folder") else emptySet()

        override fun getInputStream(uri: Uri) = bytes[uri.lastPathSegment]?.inputStream()

        override fun openRandomAccess(uri: Uri) = bytes[uri.lastPathSegment]?.let(::ByteArraySource)

        override fun getStorageFile(baseStorageFile: BaseStorageFile): StorageFile? = null

        override fun getGameRomFiles(
            game: Game,
            dataFiles: List<DataFile>,
            allowVirtualFiles: Boolean,
        ): RomFiles = throw UnsupportedOperationException()
    }

    private class EmptyMetadata : GameMetadataProvider {
        override val databaseVersion = 1

        override suspend fun sliceStamps(): Map<String, String> = emptyMap()

        override suspend fun describe(
            systemId: String,
            fallbackTitle: String,
            key: MetadataKey?,
            fileSize: Long,
        ): GameMetadata = GameMetadata(fallbackTitle, systemId, null, null, null)

        override suspend fun resolveArcade(
            setName: String,
            memberCrcs: List<String>,
        ): ArcadeResolution? = null

        override suspend fun findCartridge(crc: String): GameMetadata? = null
    }
}
