package com.omnidroid.lib.library.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Ps3ContentTest {
    private fun descriptor(category: String = "DG"): ByteArray {
        val fields = listOf("TITLE_ID" to "BLUS31426", "TITLE" to "Test PS3 Game", "CATEGORY" to category)
        val keys = fields.map { it.first + '\u0000' }.joinToString("").toByteArray()
        val data = fields.map { it.second + '\u0000' }.joinToString("").toByteArray()
        val keyStart = 20 + fields.size * 16
        val dataStart = keyStart + keys.size
        val bytes = ByteBuffer.allocate(dataStart + data.size).order(ByteOrder.LITTLE_ENDIAN)
        bytes
            .put(byteArrayOf(0, 0x50, 0x53, 0x46))
            .putInt(0x101)
            .putInt(keyStart)
            .putInt(dataStart)
            .putInt(fields.size)
        var keyOffset = 0
        var dataOffset = 0
        fields.forEach { (key, value) ->
            bytes
                .putShort(
                    keyOffset.toShort(),
                ).putShort(0x204)
                .putInt(value.length + 1)
                .putInt(value.length + 1)
                .putInt(dataOffset)
            keyOffset += key.length + 1
            dataOffset += value.length + 1
        }
        bytes.put(keys).put(data)
        return bytes.array()
    }

    @Test fun readsTitleIdentityAndDistinguishesUpdates() {
        val game = Ps3Content.parse(descriptor())!!
        assertEquals("BLUS31426", game.titleId)
        assertEquals("Test PS3 Game", game.title)
        assertTrue(game.playable)
        assertFalse(Ps3Content.parse(descriptor("GD"))!!.playable)
    }

    @Test fun rejectsTruncatedOrOutOfBoundsDescriptors() {
        assertNull(Ps3Content.parse(descriptor().copyOf(25)))
        val invalid = descriptor()
        ByteBuffer.wrap(invalid).order(ByteOrder.LITTLE_ENDIAN).putInt(12, Int.MAX_VALUE)
        assertNull(Ps3Content.parse(invalid))
    }

    @Test fun detectsFolderDescriptorsWithoutConsoleFolderHints() {
        val bytes = descriptor()
        val result =
            DetectionRules
                .identify(
                    ScanInput("PARAM.SFO", bytes.size.toLong(), "/Games/Test/PS3_GAME/PARAM.SFO", {
                        ByteArraySource(bytes)
                    }),
                )
        assertTrue(result is Detection.Ready)
        result as Detection.Ready
        assertEquals(com.omnidroid.lib.library.SystemID.PS3, result.system)
        assertEquals("Test PS3 Game", result.title)
    }
}
