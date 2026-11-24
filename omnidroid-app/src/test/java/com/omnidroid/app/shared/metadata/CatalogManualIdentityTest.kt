package com.omnidroid.app.shared.metadata

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.library.db.entity.GameIdentity
import com.omnidroid.lib.library.metadata.ArcadeResolution
import com.omnidroid.lib.library.metadata.GameMetadata
import com.omnidroid.lib.library.metadata.GameMetadataProvider
import com.omnidroid.lib.library.metadata.catalog.CatalogKeys
import com.omnidroid.lib.library.scan.MetadataKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class CatalogManualIdentityTest {
    @Test fun chosenTitleReplacesScannerIdentityWithoutRematching() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, RetrogradeDatabase::class.java).allowMainThreadQueries().build()
        try {
            val game = Game(id = 1, fileName = "old.sfc", fileUri = "file:///old.sfc",
                title = "Super Mario World", systemId = "snes", developer = null,
                coverFrontUrl = null, lastIndexedAt = 0, isManualMatch = true)
            db.gameDao().insert(listOf(game))
            db.catalogDao().putIdentity(GameIdentity(gameId = 1,
                identityKey = "old-fingerprint", lookupKey = CatalogKeys.lookup("snes", "Wrong Game"),
                system = "snes", title = "Wrong Game", alternateTitle = null, region = null,
                keyType = null, keyValue = null, matchMethod = "crc", libretroUrl = null,
                thumbnailIndexed = false, updatedAt = 0))
            var describeCalls = 0
            val local = object : GameMetadataProvider {
                override val databaseVersion = 1
                override suspend fun sliceStamps() = emptyMap<String, String>()
                override suspend fun describe(systemId: String, fallbackTitle: String, key: MetadataKey?, fileSize: Long): GameMetadata {
                    describeCalls++
                    error("A user-selected identity must not be rematched")
                }
                override suspend fun resolveArcade(setName: String, memberCrcs: List<String>): ArcadeResolution? = null
                override suspend fun findCartridge(crc: String): GameMetadata? = null
            }
            val settings = MetadataSettings(context)
            val repository = MetadataRepository(context, db, local, CatalogClient(settings), settings)
            val identity = repository.identity(game)
            assertEquals(0, describeCalls)
            assertEquals("Super Mario World", identity.title)
            assertEquals(CatalogKeys.lookup("snes", game.title), identity.lookupKey)
            assertEquals("manual", identity.matchMethod)
            assertNull(identity.alternateTitle)
            assertNotEquals("old-fingerprint", identity.identityKey)
            assertEquals(identity, db.catalogDao().identity(1))
        } finally { db.close() }
    }
}
