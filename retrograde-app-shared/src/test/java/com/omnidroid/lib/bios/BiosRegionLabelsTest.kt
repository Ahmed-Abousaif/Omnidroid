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

    @Test
    fun automaticChoiceFollowsRegionThenFatThenSlim() {
        val all = setOf(Ps2Bios.USA, Ps2Bios.EUROPE, Ps2Bios.JAPAN, "scph70012.bin")
        assertEquals(Ps2Bios.EUROPE, Ps2Bios.choose(setOf("Europe"), all, null))
        assertEquals(Ps2Bios.USA, Ps2Bios.choose(setOf("World"), all, null))
        assertEquals("scph70012.bin", Ps2Bios.choose(emptySet(), setOf("scph77001.bin", "scph70012.bin"), null))
        assertEquals(Ps2Bios.JAPAN, Ps2Bios.choose(setOf("Europe"), all, Ps2Bios.JAPAN))
        assertEquals(Ps2Bios.JAPAN_SERVED, Ps2Bios.choose(setOf("Japan"), setOf(Ps2Bios.JAPAN_SERVED), null))
        assertEquals(Ps2Bios.JAPAN, Ps2Bios.choose(setOf("Japan"), setOf(Ps2Bios.JAPAN, Ps2Bios.JAPAN_SERVED), null))
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
