package com.omnidroid.lib.library.metadata

import com.omnidroid.lib.library.metadata.catalog.CatalogKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CatalogKeysTest {
    @Test fun regionalDumpsAndDiscsShareLookupButPreserveReleaseDistinctions() {
        assertEquals(
            CatalogKeys.lookup("psx", "Final Fantasy VII"),
            CatalogKeys.lookup("psx", "Final Fantasy VII (USA) (Disc 2) [!]"),
        )
        assertNotEquals(CatalogKeys.lookup("psx", "Final Fantasy VII"), CatalogKeys.lookup("psx", "Final Fantasy VIII"))
        assertNotEquals(CatalogKeys.lookup("psx", "Final Fantasy VII"), CatalogKeys.lookup("ps2", "Final Fantasy VII"))
        assertNotEquals(
            CatalogKeys.lookup("psx", "Final Fantasy VII"),
            CatalogKeys.lookup("psx", "Final Fantasy VII (Remastered)"),
        )
    }

    @Test fun localizedNamesRetainUnicodeAndNormalizeAccents() {
        assertEquals(CatalogKeys.normalize("Pokémon"), CatalogKeys.normalize("Pokemon"))
        assertEquals("ドラゴンクエスト", CatalogKeys.normalize("ドラゴンクエスト (Japan)"))
        assertEquals("North America", CatalogKeys.region("Example (USA) (En,Fr)"))
        assertEquals("Europe", CatalogKeys.region("Example (Europe)"))
    }

    @Test fun replacementContentInvalidatesInFlightArtworkEvenWithSameTitle() {
        assertNotEquals(
            CatalogKeys.fingerprint("psx", "Example", "SERIAL", "A"),
            CatalogKeys.fingerprint("psx", "Example", "SERIAL", "B"),
        )
    }

    @Test fun arcadeBackendsShareCatalogLookupsWithoutSharingLocalIdentity() {
        assertEquals(CatalogKeys.lookup("fbneo", "Example"), CatalogKeys.lookup("mame2003plus", "Example"))
        assertNotEquals(
            CatalogKeys.fingerprint("fbneo", "Example", null, null),
            CatalogKeys.fingerprint("mame2003plus", "Example", null, null),
        )
    }
}
