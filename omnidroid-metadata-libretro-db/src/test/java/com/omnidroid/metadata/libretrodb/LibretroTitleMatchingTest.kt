package com.omnidroid.metadata.libretrodb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibretroTitleMatchingTest {
    @Test
    fun humanizesArticleSortedDumpNamesBeforeNormalizing() {
        assertEquals(
            "the legend of zelda the wind waker hd (usa asia) (en fr es)",
            LibretroTitleMatching.normalizeTitle(
                "Legend of Zelda, The - The Wind Waker HD (USA, Asia) (En,Fr,Es).wux",
            ),
        )
    }

    @Test
    fun windWakerArticleSortedDumpProducesUsaExactVariant() {
        val variants =
            LibretroTitleMatching.titleVariants(
                "Legend of Zelda, The - The Wind Waker HD (USA, Asia) (En,Fr,Es).wux",
            )
        // Wii U DAT stores: "The Legend of Zelda: The Wind Waker HD (USA)"
        // → normalizedName "the legend of zelda the wind waker hd (usa)"
        assertTrue(
            "Expected USA region variant before bare prefix match, got: $variants",
            "the legend of zelda the wind waker hd (usa)" in variants,
        )
        assertTrue(
            "Expected core title variant for DB prefix match, got: $variants",
            "the legend of zelda the wind waker hd" in variants,
        )
        assertTrue(
            "USA exact variant must appear before the bare core title",
            variants.indexOf("the legend of zelda the wind waker hd (usa)") <
                variants.indexOf("the legend of zelda the wind waker hd"),
        )
        assertFalse(
            "Article-sorted form must not leave a double-the core title",
            "legend of zelda the the wind waker hd" in variants,
        )
    }

    @Test
    fun regionScorePrefersUsaOverEuropeForUsaAsiaDump() {
        val query =
            LibretroTitleMatching.normalizeTitle(
                "Legend of Zelda, The - The Wind Waker HD (USA, Asia) (En,Fr,Es).wux",
            )!!
        val usa = LibretroTitleMatching.regionScore(query, "the legend of zelda the wind waker hd (usa)")
        val europe =
            LibretroTitleMatching.regionScore(query, "the legend of zelda the wind waker hd (europe)")
        assertTrue("USA score ($usa) should beat Europe ($europe)", usa > europe)
    }

    @Test
    fun zeldaAliasDoesNotDuplicateFranchiseName() {
        val variants =
            LibretroTitleMatching.titleVariants(
                "The Legend of Zelda - The Wind Waker HD (USA)",
            )
        assertFalse(variants.any { it.contains("the legend of the legend of zelda") })
    }

    @Test
    fun shortZeldaAliasStillExpands() {
        val variants = LibretroTitleMatching.titleVariants("zelda (usa)")
        assertTrue(variants.any { it.startsWith("the legend of zelda") })
    }

    @Test
    fun stripTagsRemovesRegionAndLanguageGroups() {
        assertEquals(
            "the legend of zelda the wind waker hd",
            LibretroTitleMatching.stripTags(
                "the legend of zelda the wind waker hd (usa asia) (en fr es)",
            ),
        )
    }
}
