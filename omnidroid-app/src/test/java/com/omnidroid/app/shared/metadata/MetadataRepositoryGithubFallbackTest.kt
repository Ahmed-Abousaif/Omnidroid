package com.omnidroid.app.shared.metadata

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class MetadataRepositoryGithubFallbackTest {
    @get:Rule val temp = TemporaryFolder()

    private val primaryUrl =
        "https://thumbnails.libretro.com/" +
            "Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/" +
            "Named_Boxarts/Super%20Mario%20World%20%28USA%29.png"
    private val githubUrl =
        "https://raw.githubusercontent.com/" +
            "libretro-thumbnails/Nintendo_-_Super_Nintendo_Entertainment_System/master/" +
            "Named_Boxarts/Super%20Mario%20World%20%28USA%29.png"
    private val igdbUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co1234.jpg"

    private fun testContext(): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val directory = temp.newFolder()
        return object : ContextWrapper(base) {
            override fun getFilesDir(): File = directory
        }
    }

    private fun pngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GREEN)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun openDb(context: Context): RetrogradeDatabase =
        Room
            .inMemoryDatabaseBuilder(context, RetrogradeDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private suspend fun insertGame(db: RetrogradeDatabase): Game {
        val game =
            Game(
                id = 1,
                fileName = "Super Mario World (USA).sfc",
                fileUri = "file:///smw.sfc",
                title = "Super Mario World",
                systemId = "snes",
                developer = null,
                coverFrontUrl = primaryUrl,
                lastIndexedAt = 1,
                fileSize = 32_768L,
                fileLastModified = 1L,
            )
        db.gameDao().insert(listOf(game))
        db.catalogDao().putIdentity(
            GameIdentity(
                gameId = 1,
                identityKey = CatalogKeys.fingerprint("snes", "Super Mario World", "CRC", "AABBCCDD"),
                lookupKey = CatalogKeys.lookup("snes", "Super Mario World"),
                system = "snes",
                title = "Super Mario World",
                alternateTitle = null,
                region = "North America",
                keyType = "CRC",
                keyValue = "AABBCCDD",
                matchMethod = "key:CRC",
                libretroUrl = primaryUrl,
                thumbnailIndexed = true,
                updatedAt = 1L,
                fileSize = 32_768L,
                fileLastModified = 1L,
            ),
        )
        return game
    }

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

    private fun coverClient(handler: (String) -> Response): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor { chain -> handler(chain.request().url.toString()) }
            .build()

    private fun response(
        url: String,
        code: Int,
        body: ByteArray = byteArrayOf(),
        mediaType: String = "image/png",
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

    private fun igdbClient(calls: AtomicInteger): OkHttpClient {
        val json = Json { encodeDefaults = true }
        return OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                calls.incrementAndGet()
                val body =
                    json.encodeToString(
                        CatalogResponse(
                            schemaVersion = 1,
                            results =
                                listOf(
                                    CatalogResolution(
                                        key = CatalogKeys.lookup("snes", "Super Mario World"),
                                        state = "matched",
                                        match =
                                            CatalogGame(
                                                provider = "igdb",
                                                providerGameId = "1070",
                                                title = "Super Mario World",
                                                sourceUrl = "https://www.igdb.com/games/super-mario-world",
                                                assets =
                                                    listOf(
                                                        CatalogArtwork(
                                                            kind = "cover",
                                                            url = igdbUrl,
                                                            imageId = "co1234",
                                                            provider = "igdb",
                                                        ),
                                                    ),
                                            ),
                                    ),
                                ),
                        ),
                    )
                response(
                    chain.request().url.toString(),
                    200,
                    body.toByteArray(Charsets.UTF_8),
                    "application/json",
                )
            }.build()
    }

    @Test
    fun primary404FallsBackToGithubBeforeIgdb() =
        runBlocking {
            val context = testContext()
            val db = openDb(context)
            try {
                val game = insertGame(db)
                val requested = mutableListOf<String>()
                val store =
                    CoverCacheStore(
                        context,
                        coverClient { url ->
                            requested += url
                            when (url) {
                                primaryUrl -> response(url, 404)
                                githubUrl -> response(url, 200, pngBytes())
                                else -> error("Unexpected image request: $url")
                            }
                        },
                    )
                val igdbCalls = AtomicInteger()
                val settings = MetadataSettings(context, enabledOverride = true)
                val repository =
                    MetadataRepository(
                        context,
                        db,
                        stubLocalProvider,
                        CatalogClient(settings, igdbClient(igdbCalls), "https://catalog.test"),
                        settings,
                    ) { store }

                val retry = repository.enrich(listOf(game))
                assertFalse(retry)
                assertEquals(listOf(primaryUrl, githubUrl), requested)
                assertEquals(0, igdbCalls.get())
                assertEquals(githubUrl, db.gameDao().selectById(1)?.coverFrontUrl)
                assertEquals("libretro", db.catalogDao().artwork(1)?.source)
            } finally {
                db.close()
            }
        }

    @Test
    fun primary503StillFallsBackToGithubBeforeIgdb() =
        runBlocking {
            val context = testContext()
            val db = openDb(context)
            try {
                val game = insertGame(db)
                val requested = mutableListOf<String>()
                val store =
                    CoverCacheStore(
                        context,
                        coverClient { url ->
                            requested += url
                            when (url) {
                                primaryUrl -> response(url, 503)
                                githubUrl -> response(url, 200, pngBytes())
                                else -> error("Unexpected image request: $url")
                            }
                        },
                    )
                val igdbCalls = AtomicInteger()
                val settings = MetadataSettings(context, enabledOverride = true)
                val repository =
                    MetadataRepository(
                        context,
                        db,
                        stubLocalProvider,
                        CatalogClient(settings, igdbClient(igdbCalls), "https://catalog.test"),
                        settings,
                    ) { store }

                val retry = repository.enrich(listOf(game))
                assertFalse(retry)
                assertEquals(listOf(primaryUrl, githubUrl), requested)
                assertEquals(0, igdbCalls.get())
                assertEquals(githubUrl, db.gameDao().selectById(1)?.coverFrontUrl)
                assertEquals("libretro", db.catalogDao().artwork(1)?.source)
            } finally {
                db.close()
            }
        }

    @Test
    fun primaryIoExceptionStillFallsBackToGithubBeforeIgdb() =
        runBlocking {
            val context = testContext()
            val db = openDb(context)
            try {
                val game = insertGame(db)
                val requested = mutableListOf<String>()
                val store =
                    CoverCacheStore(
                        context,
                        coverClient { url ->
                            requested += url
                            when (url) {
                                primaryUrl -> throw IOException("Primary host unreachable")
                                githubUrl -> response(url, 200, pngBytes())
                                else -> error("Unexpected image request: $url")
                            }
                        },
                    )
                val igdbCalls = AtomicInteger()
                val settings = MetadataSettings(context, enabledOverride = true)
                val repository =
                    MetadataRepository(
                        context,
                        db,
                        stubLocalProvider,
                        CatalogClient(settings, igdbClient(igdbCalls), "https://catalog.test"),
                        settings,
                    ) { store }

                val retry = repository.enrich(listOf(game))
                assertFalse(retry)
                assertEquals(listOf(primaryUrl, githubUrl), requested)
                assertEquals(0, igdbCalls.get())
                assertEquals(githubUrl, db.gameDao().selectById(1)?.coverFrontUrl)
            } finally {
                db.close()
            }
        }

    @Test
    fun githubTransientFailureDefersWithoutCallingIgdb() =
        runBlocking {
            val context = testContext()
            val db = openDb(context)
            try {
                val game = insertGame(db)
                val requested = mutableListOf<String>()
                val store =
                    CoverCacheStore(
                        context,
                        coverClient { url ->
                            requested += url
                            when (url) {
                                primaryUrl -> response(url, 404)
                                githubUrl -> response(url, 503)
                                else -> error("Unexpected image request: $url")
                            }
                        },
                    )
                val igdbCalls = AtomicInteger()
                val settings = MetadataSettings(context, enabledOverride = true)
                val repository =
                    MetadataRepository(
                        context,
                        db,
                        stubLocalProvider,
                        CatalogClient(settings, igdbClient(igdbCalls), "https://catalog.test"),
                        settings,
                    ) { store }

                val retry = repository.enrich(listOf(game))
                assertTrue(retry)
                assertEquals(listOf(primaryUrl, githubUrl), requested)
                assertEquals(0, igdbCalls.get())
                assertEquals(primaryUrl, db.gameDao().selectById(1)?.coverFrontUrl)
            } finally {
                db.close()
            }
        }

    @Test
    fun githubDefinitive404AfterPrimary503FallsBackToIgdb() =
        runBlocking {
            val context = testContext()
            val db = openDb(context)
            try {
                val game = insertGame(db)
                val requested = mutableListOf<String>()
                val store =
                    CoverCacheStore(
                        context,
                        coverClient { url ->
                            requested += url
                            when (url) {
                                primaryUrl -> response(url, 503)
                                githubUrl -> response(url, 404)
                                igdbUrl -> response(url, 200, pngBytes(), "image/jpeg")
                                else -> error("Unexpected image request: $url")
                            }
                        },
                    )
                val igdbCalls = AtomicInteger()
                val settings = MetadataSettings(context, enabledOverride = true)
                val repository =
                    MetadataRepository(
                        context,
                        db,
                        stubLocalProvider,
                        CatalogClient(settings, igdbClient(igdbCalls), "https://catalog.test"),
                        settings,
                    ) { store }

                val retry = repository.enrich(listOf(game))
                assertFalse(retry)
                assertEquals(listOf(primaryUrl, githubUrl, igdbUrl), requested)
                assertEquals(1, igdbCalls.get())
                assertEquals(igdbUrl, db.gameDao().selectById(1)?.coverFrontUrl)
                assertEquals("igdb", db.catalogDao().artwork(1)?.source)
            } finally {
                db.close()
            }
        }

    @Test
    fun cachedGithubMirrorReusedWithoutNetworkBeforeIgdb() =
        runBlocking {
            val context = testContext()
            val db = openDb(context)
            try {
                val game = insertGame(db)
                val networkCalls = AtomicInteger()
                val warmStore =
                    CoverCacheStore(
                        context,
                        coverClient { url ->
                            assertEquals(githubUrl, url)
                            response(url, 200, pngBytes())
                        },
                    )
                checkNotNull(warmStore.ensureGrid(1, githubUrl))

                val coldStore =
                    CoverCacheStore(
                        context,
                        coverClient { url ->
                            networkCalls.incrementAndGet()
                            error("Unexpected network request: $url")
                        },
                    )
                val igdbCalls = AtomicInteger()
                val settings = MetadataSettings(context, enabledOverride = true)
                val repository =
                    MetadataRepository(
                        context,
                        db,
                        stubLocalProvider,
                        CatalogClient(settings, igdbClient(igdbCalls), "https://catalog.test"),
                        settings,
                    ) { coldStore }

                val retry = repository.enrich(listOf(game))
                assertFalse(retry)
                assertEquals(0, networkCalls.get())
                assertEquals(0, igdbCalls.get())
                assertEquals(githubUrl, db.gameDao().selectById(1)?.coverFrontUrl)
                assertEquals("libretro", db.catalogDao().artwork(1)?.source)
            } finally {
                db.close()
            }
        }

    @Test
    fun backfillIdentityPreservesExistingLibretroUrlForGithubFallback() =
        runBlocking {
            val context = testContext()
            val db = openDb(context)
            try {
                val game =
                    Game(
                        id = 2,
                        fileName = "Super Mario World (USA).sfc",
                        fileUri = "file:///smw-backfill.sfc",
                        title = "Super Mario World",
                        systemId = "snes",
                        developer = null,
                        coverFrontUrl = primaryUrl,
                        lastIndexedAt = 1,
                        fileSize = 32_768L,
                        fileLastModified = 1L,
                    )
                db.gameDao().insert(listOf(game))
                val noThumbnailLocal =
                    object : GameMetadataProvider {
                        override val databaseVersion = 1

                        override suspend fun sliceStamps() = emptyMap<String, String>()

                        override suspend fun describe(
                            systemId: String,
                            fallbackTitle: String,
                            key: MetadataKey?,
                            fileSize: Long,
                        ) = GameMetadata(
                            name = "Super Mario World",
                            system = systemId,
                            romName = null,
                            developer = null,
                            thumbnail = null,
                            matchMethod = "filename",
                        )

                        override suspend fun resolveArcade(
                            setName: String,
                            memberCrcs: List<String>,
                        ): ArcadeResolution? = null

                        override suspend fun findCartridge(crc: String): GameMetadata? = null
                    }
                val requested = mutableListOf<String>()
                val store =
                    CoverCacheStore(
                        context,
                        coverClient { url ->
                            requested += url
                            when (url) {
                                primaryUrl -> response(url, 503)
                                githubUrl -> response(url, 200, pngBytes())
                                else -> error("Unexpected image request: $url")
                            }
                        },
                    )
                val igdbCalls = AtomicInteger()
                val settings = MetadataSettings(context, enabledOverride = true)
                val repository =
                    MetadataRepository(
                        context,
                        db,
                        noThumbnailLocal,
                        CatalogClient(settings, igdbClient(igdbCalls), "https://catalog.test"),
                        settings,
                    ) { store }

                val retry = repository.enrich(listOf(game))
                assertFalse(retry)
                assertEquals(listOf(primaryUrl, githubUrl), requested)
                assertEquals(0, igdbCalls.get())
                assertEquals(githubUrl, db.gameDao().selectById(2)?.coverFrontUrl)
            } finally {
                db.close()
            }
        }
}
