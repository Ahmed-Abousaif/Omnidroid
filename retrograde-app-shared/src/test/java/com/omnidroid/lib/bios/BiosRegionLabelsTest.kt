package com.omnidroid.lib.bios

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiosRegionLabelsTest {
    @Test
    fun parsesSingleAndMultiRegionTags() {
        assertEquals(setOf("USA"), regionLabels("Shadow of the Colossus (USA)"))
        assertEquals(setOf("Europe", "En", "Fr", "De", "Es", "It"), regionLabels("Shovel Knight (Europe) (En,Fr,De,Es,It)"))
        assertEquals(setOf("USA", "Europe"), regionLabels("Pokemon - Emerald Version (USA, Europe)"))
        assertEquals(setOf("World"), regionLabels("Lion King, The (World)"))
    }

    @Test
    fun worldTitleDoesNotExpandToEveryRegionalBios() {
        // Mirrors BiosManager policy: unknown/unmatched region → requiredBIOSFiles only.
        val regional = mapOf("USA" to "scph39001.bin", "Europe" to "scph39004.bin", "Japan" to "scph39000.bin")
        val labels = regionLabels("Some PS2 Game (World)")
        val matched = labels.intersect(regional.keys)
        assertTrue(matched.isEmpty())
        val required = if (matched.isNotEmpty()) matched.mapNotNull { regional[it] } else listOf("scph39001.bin")
        assertEquals(listOf("scph39001.bin"), required)
    }

    companion object {
        // Keep in sync with BiosManager.regionLabels.
        fun regionLabels(title: String): Set<String> {
            return Regex("""\(([^)]+)\)""")
                .findAll(title)
                .flatMap { match -> match.groupValues[1].split(',', '/', '+') }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        }
    }
}
