package com.omnidroid.lib.saves

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Ps3StateSlotsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun numberedStatesKeepTheirSlotAcrossCompressionFormatsAndGaps() {
        val directory = temporary.newFolder()
        File(directory, "slot0.SAVESTAT.zst").writeBytes(byteArrayOf(1))
        File(directory, "slot7.SAVESTAT.gz").writeBytes(byteArrayOf(2))
        File(directory, "slot4.SAVESTAT").writeBytes(byteArrayOf(3))
        val slots = Ps3StateSlots(directory)
        assertEquals(listOf(0, 1, 2, 3, 4, 7), slots.indices())
        assertEquals("slot7.SAVESTAT.gz", slots.file(7)?.name)
        assertEquals(8, slots.nextIndex())
        assertTrue(slots.info(0).exists)
        assertFalse(slots.info(1).exists)
    }

    @Test fun temporaryEmptyAndQuickStatesDoNotAllocateUserSlots() {
        val directory = temporary.newFolder()
        File(directory, "slot8.SAVESTAT.zst").writeBytes(byteArrayOf())
        File(directory, "slot3.SAVESTAT.zst.part").writeBytes(byteArrayOf(1))
        File(directory, "slot${Ps3StateSlots.QUICK_SLOT}.SAVESTAT.zst").writeBytes(byteArrayOf(1))
        File(directory, "slot${Ps3StateSlots.CAPTURE_SLOT}.SAVESTAT.zst").writeBytes(byteArrayOf(1))
        val slots = Ps3StateSlots(directory)
        assertEquals(listOf(0, 1, 2, 3), slots.indices())
        assertEquals(4, slots.nextIndex())
        assertNotNull(slots.file(Ps3StateSlots.QUICK_SLOT))
        assertNull(slots.file(8))
    }

    @Test fun replacesOnlyTheChosenSlotAfterCaptureCompletes() {
        val directory = temporary.newFolder()
        val slots = Ps3StateSlots(directory)
        val original = File(directory, "slot2.SAVESTAT.gz").apply { writeBytes(byteArrayOf(1)) }
        val other = File(directory, "slot1.SAVESTAT.zst").apply { writeBytes(byteArrayOf(9)) }
        runCatching { slots.commitCapture(2) }
        assertArrayEquals(byteArrayOf(1), original.readBytes())
        File(directory, "slot${Ps3StateSlots.CAPTURE_SLOT}.SAVESTAT.zst").writeBytes(byteArrayOf(2))
        slots.commitCapture(2)
        assertArrayEquals(byteArrayOf(2), slots.file(2)!!.readBytes())
        assertFalse(original.exists())
        assertArrayEquals(byteArrayOf(9), other.readBytes())
        assertNull(slots.file(Ps3StateSlots.CAPTURE_SLOT))
    }
}
