package com.omnidroid.lib.library

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.omnidroid.lib.bios.BiosManager
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.dao.Migrations
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.library.metadata.ArcadeResolution
import com.omnidroid.lib.library.metadata.GameMetadata
import com.omnidroid.lib.library.metadata.GameMetadataProvider
import com.omnidroid.lib.library.scan.MetadataKey
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import com.omnidroid.lib.storage.BaseStorageFile
import com.omnidroid.lib.storage.DirectoriesManager
import com.omnidroid.lib.storage.StorageListOptions
import com.omnidroid.lib.storage.StorageProvider
import com.omnidroid.lib.storage.StorageProviderRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OmnidroidLibraryManualMatchTest {
    private lateinit var context: Context
    private lateinit var database: RetrogradeDatabase
    private lateinit var sliceStamps: SliceStampStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room
                .inMemoryDatabaseBuilder(context, RetrogradeDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val prefs = SharedPreferencesHelper.getLegacySharedPreferences(context)
        prefs.edit().clear().commit()
        sliceStamps = SliceStampStore(prefs)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun manualMatchIsNotOverriddenWhenSliceStampChanges() =
        runBlocking {
            // Given existing games: one manually matched and one automatically matched
            val manualGame =
                Game(
                    id = 1,
                    fileName = "game1.dosz",
                    fileUri = "content://storage/game1.dosz",
                    title = "My Custom Manual Title",
                    coverFrontUrl = "https://example.com/custom_cover.png",
                    developer = "Custom Dev",
                    systemId = "dos",
                    lastIndexedAt = 100L,
                    fileSize = 1000L,
                    fileLastModified = 500L,
                    isAvailable = true,
                    isManualMatch = true,
                )
            val autoGame =
                Game(
                    id = 2,
                    fileName = "game2.dosz",
                    fileUri = "content://storage/game2.dosz",
                    title = "Old Auto Title",
                    coverFrontUrl = "https://example.com/old_cover.png",
                    developer = "Old Dev",
                    systemId = "dos",
                    lastIndexedAt = 100L,
                    fileSize = 1000L,
                    fileLastModified = 500L,
                    isAvailable = true,
                    isManualMatch = false,
                )
            database.gameDao().insert(listOf(manualGame, autoGame))

            // Old stamps stored before database reinstall / update
            sliceStamps.set(mapOf("dos" to "hash_v1"))

            // New stamps from updated database
            val newStamps = mapOf("dos" to "hash_v2")
            val detectedMetadata =
                GameMetadata(
                    name = "Newly Detected Title",
                    system = "dos",
                    romName = null,
                    developer = "Newly Detected Dev",
                    thumbnail = "https://example.com/new_cover.png",
                )

            val files =
                listOf(
                    BaseStorageFile(
                        name = "game1.dosz",
                        size = 1000L,
                        uri = Uri.parse("content://storage/game1.dosz"),
                        lastModified = 500L,
                    ),
                    BaseStorageFile(
                        name = "game2.dosz",
                        size = 1000L,
                        uri = Uri.parse("content://storage/game2.dosz"),
                        lastModified = 500L,
                    ),
                )

            val fakeProvider = FakeStorageProvider(files)
            val registry = StorageProviderRegistry(context, setOf(fakeProvider))
            val metadataProvider = FakeMetadataProvider(newStamps, detectedMetadata)
            val directoriesManager = DirectoriesManager(context)
            val biosManager = BiosManager(directoriesManager)

            val library =
                OmnidroidLibrary(
                    retrogradedb = database,
                    storageProviderRegistry = { registry },
                    gameMetadataProvider = { metadataProvider },
                    biosManager = biosManager,
                    sliceStamps = sliceStamps,
                    directoriesManager = directoriesManager,
                )

            // Rescan games after database update
            library.indexLibrary()

            val updatedManual = database.gameDao().selectById(1)!!
            val updatedAuto = database.gameDao().selectById(2)!!

            // Manual match must retain its title, developer, coverFrontUrl, and isManualMatch flag
            assertEquals("My Custom Manual Title", updatedManual.title)
            assertEquals("Custom Dev", updatedManual.developer)
            assertEquals("https://example.com/custom_cover.png", updatedManual.coverFrontUrl)
            assertTrue(updatedManual.isManualMatch)

            // Auto match should be updated by the new database
            assertEquals("Newly Detected Title", updatedAuto.title)
            assertEquals("https://example.com/new_cover.png", updatedAuto.coverFrontUrl)
            assertFalse(updatedAuto.isManualMatch)
        }

    @Test
    fun deepRescanOfChangedFilePreservesManualMatchAndUpdatesFileFacts() =
        runBlocking {
            val game = manualGame()
            database.gameDao().insert(listOf(game))
            val file = storageFile().copy(size = 2000L, lastModified = 800L)
            scan(listOf(file), forceDeep = true)
            val result = database.gameDao().selectById(game.id)!!
            assertEquals(game.title, result.title)
            assertEquals(game.coverFrontUrl, result.coverFrontUrl)
            assertEquals(game.developer, result.developer)
            assertEquals(2000L, result.fileSize)
            assertEquals(800L, result.fileLastModified)
            assertTrue(result.isManualMatch)
        }

    @Test
    fun renamedManualMatchKeepsCuratedMetadata() =
        runBlocking {
            val game = manualGame()
            database.gameDao().insert(listOf(game))
            scan(listOf(storageFile().copy(name = "renamed.dosz", uri = Uri.parse("content://storage/renamed.dosz"))))
            val result = database.gameDao().selectById(game.id)!!
            assertEquals("content://storage/renamed.dosz", result.fileUri)
            assertEquals(game.title, result.title)
            assertEquals(game.coverFrontUrl, result.coverFrontUrl)
            assertTrue(result.isManualMatch)
        }

    @Test
    fun missingManualMatchIsRestoredWithoutLosingUserData() =
        runBlocking {
            val game =
                manualGame().copy(
                    isAvailable = false,
                    customName = "My translation",
                    customCoverPath = "/cover.png",
                )
            database.gameDao().insert(listOf(game))
            scan(listOf(storageFile()))
            val result = database.gameDao().selectById(game.id)!!
            assertTrue(result.isAvailable)
            assertEquals(game.title, result.title)
            assertEquals(game.customName, result.customName)
            assertEquals(game.customCoverPath, result.customCoverPath)
        }

    @Test
    fun manualChoiceMadeDuringMetadataLookupWinsOverScanSnapshot() =
        runBlocking {
            val game = manualGame().copy(isManualMatch = false)
            database.gameDao().insert(listOf(game))
            scan(listOf(storageFile())) {
                database.gameDao().update(
                    listOf(game.copy(title = "User chose this during scan", isManualMatch = true)),
                )
            }
            val result = database.gameDao().selectById(game.id)!!
            assertEquals("User chose this during scan", result.title)
            assertEquals(game.coverFrontUrl, result.coverFrontUrl)
            assertTrue(result.isManualMatch)
        }

    @Test
    fun staleScanTouchPreservesLatestUserFields() {
        val snapshot = manualGame().copy(isManualMatch = false)
        database.gameDao().insert(listOf(snapshot))
        val choice = snapshot.copy(title = "User title", isManualMatch = true, customName = "Custom", isFavorite = true)
        database.gameDao().update(listOf(choice))
        database.gameDao().updateFromScan(snapshot.copy(lastIndexedAt = 999L, isAvailable = false))
        val result = database.gameDao().selectByIdBlocking(snapshot.id)!!
        assertEquals(choice.title, result.title)
        assertEquals(choice.customName, result.customName)
        assertTrue(result.isManualMatch)
        assertTrue(result.isFavorite)
        assertFalse(result.isAvailable)
        assertEquals(999L, result.lastIndexedAt)
        assertEquals(0, database.gameDao().deleteAutomaticMatch(snapshot.id))
    }

    @Test
    fun upgradePreservesLegacyMatchesAndRoomValidatesMigratedSchema() {
        val statements = mutableListOf<String>()
        database.openHelper.writableDatabase
            .query(
                "SELECT sql FROM sqlite_master WHERE type IN ('table', 'index') AND sql IS NOT NULL " +
                    "AND name NOT IN ('sqlite_sequence', 'android_metadata', 'room_master_table') " +
                    "AND name NOT IN ('game_identities', 'catalog_lookups', 'game_artwork', " +
                    "'artwork_attempts', 'index_game_identities_lookupKey') " +
                    "ORDER BY CASE type WHEN 'table' THEN 0 ELSE 1 END",
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    statements +=
                        cursor
                            .getString(0)
                            .replace(
                                Regex(
                                    ",\\s*`customCoverOrigin`\\s+TEXT\\s+NOT NULL\\s+DEFAULT\\s+(?:\\('user'\\)|'user')",
                                ),
                                "",
                            ).replace(
                                Regex(",\\s*`isManualMatch`\\s+INTEGER\\s+NOT NULL(?:\\s+DEFAULT\\s+(?:\\(0\\)|0))?"),
                                "",
                            )
                }
            }
        val name = "manual-match-migration-test"
        context.deleteDatabase(name)
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(name)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(17) {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                statements.forEach(db::execSQL)
                                db.execSQL(
                                    "INSERT INTO games (id, fileName, fileUri, title, systemId, developer, " +
                                        "coverFrontUrl, " +
                                        "lastIndexedAt, isFavorite, isAvailable) VALUES " +
                                        "(1, 'hack.dosz', 'content://storage/hack.dosz', 'My translation', 'dos', " +
                                        "'Translator', 'https://example.com/chosen.png', 100, 1, 1)",
                                )
                            }

                            override fun onUpgrade(
                                db: SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        },
                    ).build(),
            )
        helper.writableDatabase
        helper.close()
        val upgraded =
            Room
                .databaseBuilder(context, RetrogradeDatabase::class.java, name)
                .addMigrations(Migrations.VERSION_17_18, Migrations.VERSION_18_19)
                .allowMainThreadQueries()
                .build()
        try {
            val legacy = upgraded.gameDao().selectByIdBlocking(1)!!
            assertEquals("My translation", legacy.title)
            assertEquals("https://example.com/chosen.png", legacy.coverFrontUrl)
            assertTrue(legacy.isFavorite)
            assertTrue(legacy.isManualMatch)
            assertEquals("user", legacy.customCoverOrigin)
            upgraded.gameDao().insert(listOf(manualGame().copy(id = 2, isManualMatch = false)))
            assertFalse(upgraded.gameDao().selectByIdBlocking(2)!!.isManualMatch)
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    private fun manualGame() =
        Game(
            id = 1,
            fileName = "game.dosz",
            fileUri = "content://storage/game.dosz",
            title = "My ROM translation",
            coverFrontUrl = "https://example.com/translation.png",
            developer = "Translation team",
            systemId = "dos",
            lastIndexedAt = 100L,
            fileSize = 1000L,
            fileLastModified = 500L,
            isManualMatch = true,
        )

    private fun storageFile() =
        BaseStorageFile(
            name = "game.dosz",
            size = 1000L,
            uri = Uri.parse("content://storage/game.dosz"),
            lastModified = 500L,
        )

    private suspend fun scan(
        files: List<BaseStorageFile>,
        forceDeep: Boolean = false,
        onDescribe: () -> Unit = {},
    ) {
        val provider = FakeStorageProvider(files)
        val registry = StorageProviderRegistry(context, setOf(provider))
        val directories = DirectoriesManager(context)
        val metadata =
            FakeMetadataProvider(
                mapOf("dos" to "new-database"),
                GameMetadata(
                    name = "Automatic match",
                    system = "dos",
                    romName = null,
                    developer = "Auto developer",
                    thumbnail = "https://example.com/auto.png",
                ),
                onDescribe,
            )
        OmnidroidLibrary(database, { registry }, { metadata }, BiosManager(directories), sliceStamps, directories)
            .indexLibrary(forceDeep)
    }

    private class FakeStorageProvider(
        private val files: List<BaseStorageFile>,
    ) : StorageProvider {
        override val id = "fake"
        override val name = "Fake"
        override val uriSchemes = listOf("content")
        override val prefsFragmentClass = null
        override val enabledByDefault = true

        override fun listBaseStorageFiles(options: StorageListOptions): Flow<List<BaseStorageFile>> = flowOf(files)

        override fun getInputStream(uri: Uri): InputStream? = null

        override fun openRandomAccess(uri: Uri): com.omnidroid.lib.library.scan.RandomAccessBytes? = null

        override fun getStorageFile(baseStorageFile: BaseStorageFile): com.omnidroid.lib.storage.StorageFile? = null

        override fun getGameRomFiles(
            game: Game,
            dataFiles: List<com.omnidroid.lib.library.db.entity.DataFile>,
            allowVirtualFiles: Boolean,
        ): com.omnidroid.lib.storage.RomFiles = throw UnsupportedOperationException()
    }

    private class FakeMetadataProvider(
        private val stamps: Map<String, String>,
        private val metadataResult: GameMetadata,
        private val onDescribe: () -> Unit = {},
    ) : GameMetadataProvider {
        override val databaseVersion = 1

        override suspend fun sliceStamps(): Map<String, String> = stamps

        override suspend fun describe(
            systemId: String,
            fallbackTitle: String,
            key: MetadataKey?,
            fileSize: Long,
        ): GameMetadata {
            onDescribe()
            return metadataResult
        }

        override suspend fun resolveArcade(
            setName: String,
            memberCrcs: List<String>,
        ): ArcadeResolution? = null

        override suspend fun findCartridge(crc: String): GameMetadata? = null
    }
}
