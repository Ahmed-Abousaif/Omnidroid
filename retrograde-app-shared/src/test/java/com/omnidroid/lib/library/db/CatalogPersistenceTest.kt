package com.omnidroid.lib.library.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.omnidroid.lib.library.db.entity.CatalogLookup
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.library.db.entity.GameArtwork
import com.omnidroid.lib.library.db.entity.GameIdentity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CatalogPersistenceTest {
    private lateinit var db: RetrogradeDatabase
    private val game =
        Game(
            id = 1,
            fileName = "Example.zip",
            fileUri = "content://rom/1",
            title = "Example",
            systemId = "snes",
            developer = null,
            coverFrontUrl = null,
            lastIndexedAt = 1,
            fileSize = 100,
            fileLastModified = 1,
        )
    private val identity =
        GameIdentity(
            1,
            "fingerprint",
            "lookup",
            "snes",
            "Example",
            null,
            null,
            "CRC",
            "abcd",
            "key:CRC",
            null,
            false,
            1,
        )

    @Before fun setup() =
        runBlocking {
            db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        RetrogradeDatabase::class.java,
                    ).allowMainThreadQueries()
                    .build()
            db.gameDao().insert(listOf(game))
            db.catalogDao().putIdentity(identity)
        }

    @After fun close() {
        db.close()
    }

    @Test fun backgroundResultCannotOverwriteNewUserChoice() =
        runBlocking {
            db.gameDao().update(game.copy(isManualMatch = true, coverFrontUrl = "https://chosen"))
            assertFalse(db.catalogDao().applyArtwork(GameArtwork(1, "fingerprint", "igdb", "https://remote")))
            assertEquals("https://chosen", db.gameDao().selectById(1)!!.coverFrontUrl)
        }

    @Test fun backgroundResultCannotAttachToReplacedRom() =
        runBlocking {
            db.catalogDao().putIdentity(identity.copy(identityKey = "replacement"))
            assertFalse(db.catalogDao().applyArtwork(GameArtwork(1, "fingerprint", "igdb", "https://remote")))
            assertNull(db.gameDao().selectById(1)!!.coverFrontUrl)
        }

    @Test fun scanPreservesValidatedFallbackUntilHigherPriorityArtworkIsValidated() =
        runBlocking {
            assertTrue(db.catalogDao().applyArtwork(GameArtwork(1, "fingerprint", "igdb", "https://remote")))
            db.gameDao().updateFromScan(game.copy(coverFrontUrl = "https://unverified-libretro", lastIndexedAt = 2))
            assertEquals("https://remote", db.gameDao().selectById(1)!!.coverFrontUrl)
            assertTrue(
                db.catalogDao().applyArtwork(GameArtwork(1, "fingerprint", "libretro", "https://verified-libretro")),
            )
            assertEquals("https://verified-libretro", db.gameDao().selectById(1)!!.coverFrontUrl)
        }

    @Test fun deletingLocalRomDoesNotDeleteAcceptedCatalogAssociation() =
        runBlocking {
            db.catalogDao().putLookup(CatalogLookup("lookup", "matched", "{}", 0, 1))
            db.gameDao().delete(listOf(game))
            assertNotNull(db.catalogDao().lookup("lookup"))
            assertNull(db.catalogDao().identity(1))
        }
}
