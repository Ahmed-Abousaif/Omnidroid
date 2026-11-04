package com.omnidroid.lib.library.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PartialContentHasherTest {
    @Test
    fun sameContentSameHash() {
        val data = ByteArray(100_000) { it.toByte() }
        val a = PartialContentHasher.hash(ByteArraySource(data))
        val b = PartialContentHasher.hash(ByteArraySource(data.copyOf()))
        assertEquals(a, b)
    }

    @Test
    fun differentTailChangesHashForLargeFiles() {
        val dataA = ByteArray(200_000) { 1 }
        val dataB = dataA.copyOf().also { it[it.lastIndex] = 2 }
        assertNotEquals(
            PartialContentHasher.hash(ByteArraySource(dataA)),
            PartialContentHasher.hash(ByteArraySource(dataB)),
        )
    }

    @Test
    fun sizeIsPartOfHash() {
        val small = ByteArray(10) { 1 }
        val large = ByteArray(20) { 1 }
        assertNotEquals(
            PartialContentHasher.hash(ByteArraySource(small)),
            PartialContentHasher.hash(ByteArraySource(large)),
        )
    }

    @Test
    fun emptyFileHashesDeterministically() {
        val a = PartialContentHasher.hash(ByteArraySource(ByteArray(0)))
        val b = PartialContentHasher.hash(ByteArraySource(ByteArray(0)))
        assertEquals(a, b)
    }

    @Test
    fun smallFileDoesNotRequireTailWindow() {
        val data = ByteArray(100) { it.toByte() }
        val hash = PartialContentHasher.hash(ByteArraySource(data))
        assertTrue(hash.length == 40)
    }
}
