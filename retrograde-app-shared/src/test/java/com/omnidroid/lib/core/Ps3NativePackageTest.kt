package com.omnidroid.lib.core

import com.omnidroid.lib.library.CoreID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Ps3NativePackageTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun requiresTheSingleNonEmptyNativeLibrary() {
        val cores = temporary.newFolder()
        val version = File(cores, GithubCoreDownloader.CORES_VERSION).apply { mkdirs() }
        assertNull(GithubCoreDownloader.findCached(cores, CoreID.ARMSX3))
        val merged = File(version, "libomnidroid-armsx3.so")
        merged.writeBytes(byteArrayOf(1))
        assertEquals(merged, GithubCoreDownloader.findCached(cores, CoreID.ARMSX3))
        merged.writeBytes(byteArrayOf())
        assertNull(GithubCoreDownloader.findCached(cores, CoreID.ARMSX3))
    }
}
