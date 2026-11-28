package com.omnidroid.app.shared.metadata

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.omnidroid.app.mobile.feature.main.mergeOnlineCoverResults
import com.omnidroid.app.shared.covers.CoverCacheStore
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.library.db.entity.GameIdentity
import com.omnidroid.lib.library.metadata.ArcadeResolution
import com.omnidroid.lib.library.metadata.GameMetadata
import com.omnidroid.lib.library.metadata.GameMetadataProvider
import com.omnidroid.lib.library.metadata.catalog.CatalogArtwork
import com.omnidroid.lib.library.metadata.catalog.CatalogGame
import com.omnidroid.lib.library.metadata.catalog.CatalogKeys
import com.omnidroid.lib.library.metadata.catalog.CatalogResolution
import com.omnidroid.lib.library.metadata.catalog.CatalogResponse
import com.omnidroid.lib.library.scan.MetadataKey
import com.omnidroid.metadata.libretrodb.CoverSearchResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class ManualOnlineMatchTest {
    @get:Rule val temp = TemporaryFolder()

    private val igdbCoverUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co_chrono.jpg"
    private val igdbBgUrl = "https://images.igdb.com/igdb/image/upload/t_screenshot_big/sc_chrono.jpg"

    private fun testContext(): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val directory = temp.newFolder()
        return object : ContextWrapper(base) {
            override fun getFilesDir(): File = directory
        }
    }

    private fun openDb(context: Context): RetrogradeDatabase =
        Room
            .inMemoryDatabaseBuilder(context, RetrogradeDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private fun pngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.CYAN)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun response(
        url: String,
        code: Int,
        body: ByteArray,
        mediaType: String,
    ): Response =
        Response
            .Builder()
            .request(
                Request
                    .Builder()
                    .url(url)
                    .build(),
            ).protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(body.toResponseBody(mediaType.toMediaType()))
            .build()

    private val stubLocalProvider =
        object : GameMetadataProvider {
            override val databaseVersion = 1

            override suspend fun sliceStamps() = emptyMap<String, String>()

            override suspend fun describe(
                systemId: String,
                fallbackTitle: String,
                key: MetadataKey?,
                fileSize: Long,
            ): GameMetadata = error("Unexpected describe call")

            override suspend fun resolveArcade(
                setName: String,
                memberCrcs: List<String>,
            ): ArcadeResolution? = null

            override suspend fun findCartridge(crc: String): GameMetadata? = null
        }

    private fun insertWronglyNamedGame(db: RetrogradeDatabase): Game =
        runBlocking {
            val game =
                Game(
                    id = 1,
                    fileName = "ct_hack.sfc",
                    fileUri = "file:///ct_hack.sfc",
                    title = "Wrong Name",
                    systemId = "snes",
                    developer = null,
                    coverFrontUrl = null,
                    lastIndexedAt = 1L,
                    customName = "Old Custom Name",
                    fileSize = 4096L,
                    fileLastModified = 1L,
                )
            db.gameDao().insert(listOf(game))
            db.catalogDao().putIdentity(
                GameIdentity(
                    gameId = 1,
                    identityKey = CatalogKeys.fingerprint("snes", "Wrong Name", null, null),
                    lookupKey = CatalogKeys.lookup("snes", "Wrong Name"),
                    system = "snes",
                    title = "Wrong Name",
                    alternateTitle = null,
                    region = "North America",
                    keyType = null,
                    keyValue = null,
                    matchMethod = "filename",
                    libretroUrl = null,
                    thumbnailIndexed = false,
                    updatedAt = 1L,
                    fileSize = 4096L,
                    fileLastModified = 1L,
                ),
            )
            game
        }

    @Test
    fun mergeOnlineCoverResultsPlacesOnlineAtTop() {
        val local =
            listOf(
                CoverSearchResult("Local A", "https://thumbnails.libretro.com/a.png", 90),
                CoverSearchResult("Local B", "https://thumbnails.libretro.com/b.png", 80),
            )
        val online =
            listOf(
                CoverSearchResult("IGDB 1", igdbCoverUrl, 100),
                CoverSearchResult("Local A Online", "https://thumbnails.libretro.com/a.png", 0),
            )

        val merged = mergeOnlineCoverResults(local, online)
        assertEquals(
            listOf(igdbCoverUrl, "https://thumbnails.libretro.com/a.png", "https://thumbnails.libretro.com/b.png"),
            merged.map { it.coverUrl },
        )
        assertEquals("IGDB 1", merged.first().title)
    }

    @Test
    fun fullOnlineMatchPersistsAndHydratesLikeServerIgdbMatch() =
        runBlocking {
            val context = testContext()
            val db = openDb(context)
            try {
                val game = insertWronglyNamedGame(db)
                val json = Json { encodeDefaults = true }
                val searchMatch =
                    CatalogGame(
                        provider = "igdb",
                        providerGameId = "101",
                        title = "Chrono Trigger",
                        sourceUrl = "https://www.igdb.com/games/chrono-trigger",
                        assets =
                            listOf(
                                CatalogArtwork(
                                    kind = "cover",
                                    url = igdbCoverUrl,
                                    imageId = "co_chrono",
                                    provider = "igdb",
                                ),
                            ),
                        detailsComplete = false,
                    )
                val hydratedMatch =
                    searchMatch.copy(
                        description = "A time-travel RPG classic.",
                        genres = listOf("Role-playing (RPG)"),
                        developer = "Square",
                        publisher = "Square",
                        releaseDate = "1995-03-11",
                        rating = 94.0,
                        detailsComplete = true,
                        assets =
                            searchMatch.assets +
                                CatalogArtwork(
                                    kind = "background",
                                    url = igdbBgUrl,
                                    imageId = "sc_chrono",
                                    provider = "igdb",
                                ),
                    )
                val paths = mutableListOf<String>()
                val apiClient =
                    OkHttpClient
                        .Builder()
                        .addInterceptor { chain ->
                            val path = chain.request().url.encodedPath
                            paths += path
                            val key = CatalogKeys.lookup("snes", "Chrono Trigger")
                            val payload =
                                when (path) {
                                    "/api/metadata/search" ->
                                        CatalogResponse(
                                            results =
                                                listOf(
                                                    CatalogResolution(
                                                        key = key,
                                                        state = "ambiguous",
                                                        candidates = listOf(searchMatch),
                                                    ),
                                                ),
                                        )
                                    "/api/metadata/resolve" ->
                                        CatalogResponse(
                                            results =
                                                listOf(
                                                    CatalogResolution(
                                                        key = key,
                                                        state = "matched",
                                                        match = hydratedMatch,
                                                    ),
                                                ),
                                        )
                                    else -> error("Unexpected endpoint: $path")
                                }
                            response(
                                chain.request().url.toString(),
                                200,
                                json.encodeToString(payload).toByteArray(Charsets.UTF_8),
                                "application/json",
                            )
                        }.build()
                val coverStore =
                    CoverCacheStore(
                        context,
                        OkHttpClient
                            .Builder()
                            .addInterceptor { chain ->
                                response(
                                    chain.request().url.toString(),
                                    200,
                                    pngBytes(),
                                    "image/jpeg",
                                )
                            }.build(),
                    )
                val settings = MetadataSettings(context, enabledOverride = true)
                val repository =
                    MetadataRepository(
                        context,
                        db,
                        stubLocalProvider,
                        CatalogClient(settings, apiClient, "https://catalog.test"),
                        settings,
                    ) { coverStore }

                val candidates = repository.search(game, "Chrono Trigger")
                assertEquals(1, candidates.size)
                assertNotNull(candidates[0].catalogMatch)

                repository.applyManualMatch(
                    game = game,
                    newTitle = candidates[0].title,
                    newCoverUrl = candidates[0].coverUrl,
                    catalogMatch = candidates[0].catalogMatch,
                )

                val saved = checkNotNull(db.gameDao().selectById(1))
                assertEquals("Chrono Trigger", saved.title)
                assertNull(saved.customName)
                assertEquals(igdbCoverUrl, saved.coverFrontUrl)
                assertEquals("Square", saved.developer)
                assertTrue(saved.isManualMatch)

                val artwork = checkNotNull(db.catalogDao().artwork(1))
                assertEquals("igdb", artwork.source)
                assertEquals(igdbCoverUrl, artwork.sourceUrl)

                val callsBeforeDetails = paths.size
                val details = checkNotNull(repository.details(saved))
                assertEquals(callsBeforeDetails, paths.size)
                assertEquals("A time-travel RPG classic.", details.description)
                assertEquals(igdbBgUrl, details.artwork("background", null)?.url)
                assertEquals(listOf("/api/metadata/search", "/api/metadata/resolve"), paths)
            } finally {
                db.close()
            }
        }

    @Test
    fun coverOnlyOnlineMatchAlsoPersistsFullIgdbMetadata() =
        runBlocking {
            val context = testContext()
            val db = openDb(context)
            try {
                val game = insertWronglyNamedGame(db)
                val json = Json { encodeDefaults = true }
                val igdbMatch =
                    CatalogGame(
                        provider = "igdb",
                        providerGameId = "202",
                        title = "Chrono Trigger",
                        sourceUrl = "https://www.igdb.com/games/chrono-trigger",
                        description = "Saved even in cover-only mode.",
                        developer = "Square",
                        detailsComplete = true,
                        assets =
                            listOf(
                                CatalogArtwork(
                                    kind = "cover",
                                    url = igdbCoverUrl,
                                    imageId = "co_chrono",
                                    provider = "igdb",
                                ),
                                CatalogArtwork(
                                    kind = "background",
                                    url = igdbBgUrl,
                                    imageId = "sc_chrono",
                                    provider = "igdb",
                                ),
                            ),
                    )
                val resolveCalls = mutableListOf<String>()
                val apiClient =
                    OkHttpClient
                        .Builder()
                        .addInterceptor { chain ->
                            resolveCalls += chain.request().url.encodedPath
                            response(
                                chain.request().url.toString(),
                                200,
                                json.encodeToString(CatalogResponse()).toByteArray(Charsets.UTF_8),
                                "application/json",
                            )
                        }.build()
                val coverStore =
                    CoverCacheStore(
                        context,
                        OkHttpClient
                            .Builder()
                            .addInterceptor { chain ->
                                response(
                                    chain.request().url.toString(),
                                    200,
                                    pngBytes(),
                                    "image/jpeg",
                                )
                            }.build(),
                    )
                val settings = MetadataSettings(context, enabledOverride = true)
                val repository =
                    MetadataRepository(
                        context,
                        db,
                        stubLocalProvider,
                        CatalogClient(settings, apiClient, "https://catalog.test"),
                        settings,
                    ) { coverStore }

                repository.applyManualMatch(
                    game = game,
                    newTitle = null,
                    newCoverUrl = igdbCoverUrl,
                    catalogMatch = igdbMatch,
                )

                val saved = checkNotNull(db.gameDao().selectById(1))
                assertEquals("Wrong Name", saved.title)
                assertEquals("Old Custom Name", saved.customName)
                assertEquals(igdbCoverUrl, saved.coverFrontUrl)
                assertEquals("Square", saved.developer)
                assertTrue(saved.isManualMatch)

                val artwork = checkNotNull(db.catalogDao().artwork(1))
                assertEquals("igdb", artwork.source)
                assertEquals(igdbCoverUrl, artwork.sourceUrl)

                val details = checkNotNull(repository.details(saved))
                assertEquals("Saved even in cover-only mode.", details.description)
                assertEquals(igdbBgUrl, details.artwork("background", null)?.url)
                assertEquals(0, resolveCalls.size)

                // Switching back to a non-catalog (local libretro) cover clears IGDB artwork provenance.
                repository.applyManualMatch(
                    game = saved,
                    newTitle = null,
                    newCoverUrl = "https://thumbnails.libretro.com/snes/Named_Boxarts/Example.png",
                    catalogMatch = null,
                )
                assertNull(db.catalogDao().artwork(1))
                assertFalse(db.gameDao().selectById(1)?.coverFrontUrl == igdbCoverUrl)
            } finally {
                db.close()
            }
        }
}
