package com.omnidroid.lib.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileUriNormalizerTest {

    @Test
    fun normalizeToUriStringConvertsRawPathsToFileUris() {
        assertEquals(
            "file:///storage/emulated/0/Roms/nes/mario.nes",
            FileUriNormalizer.normalizeToUriString("/storage/emulated/0/Roms/nes/mario.nes"),
        )
        assertEquals(
            "file:///storage/emulated/0/Roms/nes/mario.nes",
            FileUriNormalizer.normalizeToUriString("/sdcard/Roms/nes/mario.nes"),
        )
    }

    @Test
    fun normalizeToUriStringKeepsExistingFileAndContentUris() {
        val contentUri = "content://com.android.externalstorage.documents/document/primary%3ARoms%2Fnes%2Fmario.nes"
        assertEquals(contentUri, FileUriNormalizer.normalizeToUriString(contentUri))

        assertEquals(
            "file:///storage/emulated/0/Roms/nes/mario.nes",
            FileUriNormalizer.normalizeToUriString("file:///storage/emulated/0/Roms/nes/mario.nes"),
        )
    }

    @Test
    fun getCanonicalPathExtractsFromSafTreeAndDocumentUris() {
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3ARoms/document/primary%3ARoms%2Fnes%2Fmario.nes"
        val docUri = "content://com.android.externalstorage.documents/document/primary%3ARoms%2Fnes%2Fmario.nes"

        assertEquals("/storage/emulated/0/Roms/nes/mario.nes", FileUriNormalizer.getCanonicalPath(treeUri))
        assertEquals("/storage/emulated/0/Roms/nes/mario.nes", FileUriNormalizer.getCanonicalPath(docUri))
    }

    @Test
    fun getCanonicalPathExtractsFromSdCardWithUuid() {
        val treeUri = "content://com.android.externalstorage.documents/tree/1234-5678%3ARoms/document/1234-5678%3ARoms%2Fsnes%2Fzelda.sfc"
        assertEquals("/storage/1234-5678/Roms/snes/zelda.sfc", FileUriNormalizer.getCanonicalPath(treeUri))
    }

    @Test
    fun getCanonicalPathExtractsFromFileUrisAndRawPaths() {
        assertEquals(
            "/storage/emulated/0/Roms/nes/mario.nes",
            FileUriNormalizer.getCanonicalPath("file:///storage/emulated/0/Roms/nes/mario.nes"),
        )
        assertEquals(
            "/storage/emulated/0/Roms/nes/mario.nes",
            FileUriNormalizer.getCanonicalPath("file:///sdcard/Roms/nes/mario.nes"),
        )
        assertEquals(
            "/storage/emulated/0/Roms/nes/mario.nes",
            FileUriNormalizer.getCanonicalPath("/sdcard/Roms/nes/mario.nes"),
        )
    }

    @Test
    fun percentEncodingAndUnicodeHandling() {
        val encodedUri = "file:///storage/emulated/0/Roms/nes/Super%20Mario%20Bros.nes"
        assertEquals("/storage/emulated/0/Roms/nes/Super Mario Bros.nes", FileUriNormalizer.getCanonicalPath(encodedUri))
        assertEquals("Super Mario Bros.nes", FileUriNormalizer.extractFileName(encodedUri))

        val unicodeUri = "file:///storage/emulated/0/Roms/snes/%E3%82%BC%E3%83%AB%E3%83%80%E3%81%AE%E4%BC%9D%E8%AA%AC.sfc"
        assertEquals("ゼルダの伝説.sfc", FileUriNormalizer.extractFileName(unicodeUri))
        assertEquals("/storage/emulated/0/Roms/snes/ゼルダの伝説.sfc", FileUriNormalizer.getCanonicalPath(unicodeUri))
    }

    @Test
    fun extractExtensionExtractsCorrectExtension() {
        assertEquals("nes", FileUriNormalizer.extractExtension("file:///storage/emulated/0/mario.nes"))
        assertEquals("sfc", FileUriNormalizer.extractExtension("/storage/emulated/0/zelda.SFC"))
        assertEquals("chd", FileUriNormalizer.extractExtension("content://.../primary%3Agame.CHD?query=1"))
        assertNull(FileUriNormalizer.extractExtension("/storage/emulated/0/no_extension"))
    }

    @Test
    fun matchingBetweenSafAndFileRepresentations() {
        val safTreeUri = "content://com.android.externalstorage.documents/tree/primary%3ARoms/document/primary%3ARoms%2Fnes%2FSuper%20Mario.nes"
        val safDocUri = "content://com.android.externalstorage.documents/document/primary%3ARoms%2Fnes%2FSuper%20Mario.nes"
        val rawPath = "/storage/emulated/0/Roms/nes/Super Mario.nes"
        val sdcardPath = "/sdcard/Roms/nes/Super Mario.nes"
        val fileUri = "file:///storage/emulated/0/Roms/nes/Super%20Mario.nes"

        assertTrue("SAF Tree matches SAF Doc", FileUriNormalizer.matches(safTreeUri, safDocUri))
        assertTrue("SAF Tree matches raw path", FileUriNormalizer.matches(safTreeUri, rawPath))
        assertTrue("SAF Tree matches /sdcard path", FileUriNormalizer.matches(safTreeUri, sdcardPath))
        assertTrue("SAF Doc matches file URI", FileUriNormalizer.matches(safDocUri, fileUri))
        assertTrue("File URI matches raw path", FileUriNormalizer.matches(fileUri, rawPath))
    }

    @Test
    fun matchingDoesNotMatchOnFileNameAloneAcrossDirectories() {
        val gameInNes = "/storage/emulated/0/Roms/nes/Super Mario.nes"
        val gameInFavorites = "/storage/emulated/0/Roms/favorites/Super Mario.nes"

        assertFalse(FileUriNormalizer.matches(gameInNes, gameInFavorites))
    }

    @Test
    fun thirdPartyFileManagerUrisDoNotRecurse() {
        val samsungUri = "content://com.sec.android.app.myfiles.FileProvider/root/storage/emulated/0/Download/game.zip"
        val googleFilesUri = "content://com.google.android.apps.nbu.files.provider/1/12345"
        val mediaUri = "content://media/external/file/98765"

        assertNull(FileUriNormalizer.getDocumentId(samsungUri))
        assertNull(FileUriNormalizer.getCanonicalPath(samsungUri))
        assertNull(FileUriNormalizer.getDocumentId(googleFilesUri))
        assertNull(FileUriNormalizer.getCanonicalPath(googleFilesUri))
        assertNull(FileUriNormalizer.getDocumentId(mediaUri))
        assertNull(FileUriNormalizer.getCanonicalPath(mediaUri))

        assertFalse(FileUriNormalizer.matches(samsungUri, googleFilesUri))
        assertTrue(FileUriNormalizer.matches(samsungUri, samsungUri))
    }
}
