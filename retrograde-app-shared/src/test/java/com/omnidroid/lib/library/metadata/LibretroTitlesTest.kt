package com.omnidroid.lib.library.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibretroTitlesTest {
    @Test
    fun humanizeMovesTrailingArticles() {
        assertEquals("The Lion King (World)", LibretroTitles.humanize("Lion King, The (World)"))
        assertEquals(
            "The Legend of Zelda - A Link to the Past (USA)",
            LibretroTitles.humanize("Legend of Zelda, The - A Link to the Past (USA)"),
        )
        assertEquals("A Link to the Past (USA)", LibretroTitles.humanize("Link to the Past, A (USA)"))
        assertEquals("An American Tail (USA)", LibretroTitles.humanize("American Tail, An (USA)"))
    }

    @Test
    fun humanizeLeavesNaturalTitlesAlone() {
        assertEquals("Shadow of the Colossus (USA)", LibretroTitles.humanize("Shadow of the Colossus (USA)"))
        assertEquals("The Lion King", LibretroTitles.humanize("The Lion King"))
        assertEquals("Pokemon - Emerald Version (USA, Europe)", LibretroTitles.humanize("Pokemon - Emerald Version (USA, Europe)"))
    }

    @Test
    fun detectsArticleSortedTitles() {
        assertTrue(LibretroTitles.isArticleSorted("Lion King, The (World)"))
        assertTrue(LibretroTitles.isArticleSorted("Legend of Zelda, The - A Link to the Past (USA)"))
        assertFalse(LibretroTitles.isArticleSorted("The Lion King (World)"))
        assertFalse(LibretroTitles.isArticleSorted("Shadow of the Colossus (USA)"))
    }
}
