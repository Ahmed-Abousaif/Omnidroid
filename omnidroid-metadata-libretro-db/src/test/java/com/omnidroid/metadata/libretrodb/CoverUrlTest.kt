package com.omnidroid.metadata.libretrodb

import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.SystemID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverUrlTest {
    private val playstation2 =
        GameSystem(
            id = SystemID.PS2,
            libretroFullName = "Sony - PlayStation 2",
            titleResId = 0,
            shortTitleResId = 0,
            systemCoreConfigs = emptyList(),
            uniqueExtensions = emptyList(),
        )

    @Test
    fun fallbackKeepsExclamationMarks() {
        val url =
            CoverSearcher.computeCoverUrl(
                playstation2,
                "Yu-Gi-Oh! The Duelists of the Roses (USA)",
            )
        assertTrue(url!!.contains("Yu-Gi-Oh%21"))
        assertTrue(!url.contains("Yu-Gi-Oh_"))
    }

    @Test
    fun fallbackReplacesColon() {
        val url = CoverSearcher.computeCoverUrl(playstation2, "WarioWare: Smooth Moves (USA)")
        assertTrue(url!!.contains("WarioWare_%20Smooth"))
    }

    @Test
    fun storedPathIsEncodedWithoutRenaming() {
        val url =
            CoverSearcher.computeCoverUrl(
                playstation2,
                "ignored",
                "Named_Boxarts/Yu-Gi-Oh! The Duelists of the Roses (USA).png",
            )
        assertEquals(
            "https://thumbnails.libretro.com/Sony%20-%20PlayStation%202/" +
                "Named_Boxarts/Yu-Gi-Oh%21%20The%20Duelists%20of%20the%20Roses%20%28USA%29.png",
            url,
        )
    }
}
