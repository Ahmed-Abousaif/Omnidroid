package com.omnidroid.app.testing

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.request.SuccessResult
import com.omnidroid.R
import com.omnidroid.app.OmnidroidApplication
import com.omnidroid.app.shared.covers.CoverCacheStore
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.app.shared.metadata.CatalogClient
import com.omnidroid.app.shared.metadata.MetadataRepository
import com.omnidroid.app.shared.metadata.MetadataSettings
import com.omnidroid.lib.bios.BiosManager
import com.omnidroid.lib.library.OmnidroidLibrary
import com.omnidroid.lib.library.SliceStampStore
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.DataFile
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.library.scan.ByteArraySource
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import com.omnidroid.lib.storage.*
import com.omnidroid.metadata.libretrodb.LibretroDBMetadataProvider
import com.omnidroid.metadata.libretrodb.db.LibretroDBManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32

/** Opt-in live test. Run against the isolated backend suite via adb reverse tcp:3300. */
@RunWith(AndroidJUnit4::class)
class MetadataEndToEndTest {
    @Test(timeout = 180_000)
    fun scanLocalDatabaseArtworkFallbackAndDurableOfflineReuse(): Unit = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val origin = arguments.getString("catalogOrigin")
        assumeTrue("Set -e catalogOrigin http://127.0.0.1:3300 for the live suite", origin != null)
        require(origin!!.startsWith("http://127.0.0.1:") || origin.startsWith("https://localhost:"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        val onlineKey = context.getString(R.string.pref_key_online_artwork)
        val wasEnabled = prefs.getBoolean(onlineKey, true)
        prefs.edit().putBoolean(onlineKey, true).commit()
        val databaseName = "metadata-e2e-${UUID.randomUUID()}"
        fun openLibrary() = Room.databaseBuilder(context, RetrogradeDatabase::class.java, databaseName).build()
        var libraryDb = openLibrary()
        val manager = LibretroDBManager(context)
        val local = LibretroDBMetadataProvider(manager)
        val root = File(context.cacheDir, databaseName).apply { mkdirs() }
        val files = listOf("local.sfc", "fallback.sfc", "duplicate.sfc").mapIndexed { index, name ->
            File(root, name).apply { writeBytes(ByteArray(32768) { (it + if (index == 0) 23 else 47).toByte() }) }
        }
        fun crc(file: File) = CRC32().apply { update(file.readBytes()) }.value.toString(16).uppercase().padStart(8, '0')
        val fixtureIds = listOf(-901001, -901002)
        val settings = MetadataSettings(context)
        val serverRequests = AtomicInteger()
        val transport = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS).callTimeout(100, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain -> serverRequests.incrementAndGet(); chain.proceed(chain.request()) }.build()
        val client = CatalogClient(settings, transport, origin)
        val cache = CoverUtils.coverCache(context)
        try {
            // Synthetic bytes exercise real hashing and real SQLite metadata lookup;
            // no copyrighted ROM, mock metadata provider or mocked HTTP response is used.
            for (index in 0..1) {
                manager.dbInstance.openHelper.writableDatabase.execSQL(
                    "INSERT INTO games(id, name, system, crc32, size, rawName, thumbnailPath) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(fixtureIds[index], "Super Mario World", "snes", crc(files[index]), 32768,
                        "Super Mario World (USA)", if (index == 0) "Named_Boxarts/Super Mario World (USA).png" else "Named_Boxarts/__omnidroid_e2e_missing__.png"),
                )
            }
            val provider = FixtureFiles(files)
            val registry = StorageProviderRegistry(context, setOf(provider))
            val directories = DirectoriesManager(context)
            val stamps = SliceStampStore(context.getSharedPreferences(databaseName, Context.MODE_PRIVATE))
            fun scanner() = OmnidroidLibrary(libraryDb, { registry }, { local }, BiosManager(directories), stamps, directories)
            scanner().indexLibrary(forceDeep = true)
            var games = files.map { checkNotNull(libraryDb.gameDao().selectByFileUri(Uri.fromFile(it).toString())) }
            assertEquals(3, games.size)
            for (game in games) {
                assertEquals("Super Mario World", game.title)
                assertEquals("snes", game.systemId)
                val identity = checkNotNull(libraryDb.catalogDao().identity(game.id))
                assertEquals("CRC", identity.keyType)
                assertEquals(crc(File(Uri.parse(game.fileUri).path!!)), identity.keyValue)
                assertEquals("key:CRC", identity.matchMethod)
            }
            var repository = MetadataRepository(context, libraryDb, local, client, settings)
            repository.enrich(listOf(games[0]))
            val localGame = checkNotNull(libraryDb.gameDao().selectById(games[0].id))
            assertEquals("libretro", libraryDb.catalogDao().artwork(localGame.id)?.source)
            assertTrue(localGame.coverFrontUrl!!.startsWith("https://raw.githubusercontent.com/libretro-thumbnails/"))
            assertNotNull(cache.gridFile(localGame.id, localGame.coverFrontUrl!!))
            assertEquals("Working libretro source must not contact metadata service", 0, serverRequests.get())

            repository.enrich(games.drop(1))
            val fallback = checkNotNull(libraryDb.gameDao().selectById(games[1].id))
            val duplicate = checkNotNull(libraryDb.gameDao().selectById(games[2].id))
            assertEquals("igdb", libraryDb.catalogDao().artwork(fallback.id)?.source)
            assertTrue(fallback.coverFrontUrl!!.startsWith("https://images.igdb.com/"))
            assertEquals(fallback.coverFrontUrl, duplicate.coverFrontUrl)
            assertNotNull(cache.gridFile(fallback.id, fallback.coverFrontUrl!!))
            assertNotNull(cache.gridFile(duplicate.id, duplicate.coverFrontUrl!!))
            assertEquals("Duplicate ROMs must share one catalog request", 1, serverRequests.get())

            val sourceFiles = File(context.filesDir, "${CoverCacheStore.COVER_CACHE_DIR}/sources").listFiles().orEmpty().map { it.name }.toSet()
            val details = checkNotNull(repository.details(fallback))
            assertEquals("1070", details.providerGameId)
            assertFalse(details.description.isNullOrBlank())
            assertEquals(1, serverRequests.get())
            assertNotNull(cache.ensureDetail(fallback.id, fallback.coverFrontUrl!!))
            assertEquals(sourceFiles, File(context.filesDir, "${CoverCacheStore.COVER_CACHE_DIR}/sources").listFiles().orEmpty().map { it.name }.toSet())
            val imageLoader = CoverUtils.buildImageLoader(context)
            try {
                assertTrue(imageLoader.execute(CoverUtils.coverRequest(context, fallback).newBuilder().allowHardware(false).build()) is SuccessResult)
            } finally { imageLoader.shutdown() }

            val identity = checkNotNull(libraryDb.catalogDao().identity(fallback.id))
            val accepted = checkNotNull(libraryDb.catalogDao().lookup(identity.lookupKey))
            assertEquals("matched", accepted.state)
            assertEquals(0L, accepted.retryAt)
            libraryDb.close()
            libraryDb = openLibrary()
            assertEquals(accepted, libraryDb.catalogDao().lookup(identity.lookupKey))
            val offlineRequests = AtomicInteger()
            val offlineClient = CatalogClient(settings, OkHttpClient.Builder().addInterceptor {
                offlineRequests.incrementAndGet(); throw IOException("Intentional offline phase")
            }.build(), origin)
            repository = MetadataRepository(context, libraryDb, local, offlineClient, settings)
            games = files.map { checkNotNull(libraryDb.gameDao().selectByFileUri(Uri.fromFile(it).toString())) }
            repository.enrich(games)
            assertEquals("1070", repository.details(games[1])?.providerGameId)
            assertEquals("Permanent Room records and saved images must work offline", 0, offlineRequests.get())

            val selected = games[1].copy(title = "User selected title", customName = "User label", isManualMatch = true)
            libraryDb.gameDao().update(selected)
            scanner().indexLibrary(forceDeep = true)
            repository.enrich(listOf(selected))
            assertEquals(selected.title, libraryDb.gameDao().selectById(selected.id)?.title)
            assertEquals(selected.customName, libraryDb.gameDao().selectById(selected.id)?.customName)
            assertEquals(selected.coverFrontUrl, libraryDb.gameDao().selectById(selected.id)?.coverFrontUrl)
            assertEquals(0, offlineRequests.get())
            Log.i("MetadataE2E", "PASS real scan -> CRC/local SQLite -> libretro source -> IGDB via local Nitro/Postgres -> decoded artwork -> durable offline reuse; catalogRequests=${serverRequests.get()}")
        } finally {
            cache.attachAttempts((context.applicationContext as OmnidroidApplication).retrogradeDb.catalogDao())
            libraryDb.close()
            context.deleteDatabase(databaseName)
            fixtureIds.forEach { id -> manager.dbInstance.openHelper.writableDatabase.execSQL("DELETE FROM games WHERE id = ?", arrayOf(id)) }
            manager.dbInstance.close()
            root.listFiles().orEmpty().forEach(File::delete)
            root.delete()
            prefs.edit().putBoolean(onlineKey, wasEnabled).commit()
        }
    }

    private class FixtureFiles(private val files: List<File>) : StorageProvider {
        override val id = "metadata-e2e"
        override val name = "Synthetic metadata test files"
        override val uriSchemes = listOf("file")
        override val prefsFragmentClass = null
        override val enabledByDefault = true
        override fun listBaseStorageFiles(options: StorageListOptions) = flowOf(files.map {
            BaseStorageFile(it.name, it.length(), Uri.fromFile(it), it.parent, it.lastModified())
        })
        override fun getInputStream(uri: Uri) = File(uri.path!!).inputStream()
        override fun openRandomAccess(uri: Uri) = ByteArraySource(File(uri.path!!).readBytes())
        override fun getStorageFile(baseStorageFile: BaseStorageFile): StorageFile? = null
        override fun getGameRomFiles(game: Game, dataFiles: List<DataFile>, allowVirtualFiles: Boolean): RomFiles = error("Playback is outside this metadata test")
    }
}
