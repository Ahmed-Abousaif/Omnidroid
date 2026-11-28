package com.omnidroid.app.shared.covers

import android.app.Application
import com.omnidroid.metadata.libretrodb.CoverSearcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class LibretroMirrorPolicyTest {
    @Test fun exactIndexedFilenameMapsOnlyToOfficialLibretroSource() {
        val source =
            "https://thumbnails.libretro.com/" +
                "Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/" +
                "Named_Boxarts/Super%20Mario%20World%20(USA).png"
        val mirror = CoverSearcher.libretroMirrorUrl(source)!!
        assertEquals(
            "https://raw.githubusercontent.com/libretro-thumbnails/Nintendo_-_Super_Nintendo_Entertainment_System/master/Named_Boxarts/Super%20Mario%20World%20(USA).png",
            mirror,
        )
        assertTrue(CoverUtils.isAllowedRemoteCoverUrl(mirror))
        assertNull(CoverSearcher.libretroMirrorUrl(source.replace("thumbnails.libretro.com", "evil.example")))
        assertFalse(CoverUtils.isAllowedRemoteCoverUrl(mirror.replace("libretro-thumbnails/", "someone-else/")))
        assertFalse(CoverUtils.isAllowedRemoteCoverUrl(mirror.replace("https:", "http:")))
        assertFalse(CoverUtils.isAllowedRemoteCoverUrl(mirror.replace("Named_Boxarts", "arbitrary-files")))
    }
}
