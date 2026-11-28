package com.omnidroid.lib.ra

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class UnwrappedSaveState(
    val coreState: ByteArray,
    val raProgress: ByteArray?,
)

/**
 * Appends/strips a versioned RetroAchievements softcore progress trailer after the
 * libretro core state blob:
 *   [coreStateBytes][raProgressBytes][uint32 BE progressLen][uint16 BE version=1][4B magic "OMRA"]
 * Ensures `retro_unserialize` receives the exact untouched core bytes even on cores that
 * validate exact serialized byte length, while older/missing-trailer states decode safely.
 */
object RaSaveStateProgressCodec {
    private val MAGIC = byteArrayOf('O'.code.toByte(), 'M'.code.toByte(), 'R'.code.toByte(), 'A'.code.toByte())
    private const val VERSION: Short = 1
    private const val TRAILER_SIZE = 4 + 2 + 4 // progressLen(4) + version(2) + magic(4)

    fun wrap(
        coreState: ByteArray,
        raProgress: ByteArray?,
    ): ByteArray {
        if (raProgress == null || raProgress.isEmpty()) return coreState
        val out = ByteArray(coreState.size + raProgress.size + TRAILER_SIZE)
        System.arraycopy(coreState, 0, out, 0, coreState.size)
        System.arraycopy(raProgress, 0, out, coreState.size, raProgress.size)
        val bb =
            ByteBuffer
                .wrap(out, coreState.size + raProgress.size, TRAILER_SIZE)
                .order(ByteOrder.BIG_ENDIAN)
        bb.putInt(raProgress.size)
        bb.putShort(VERSION)
        bb.put(MAGIC)
        return out
    }

    fun unwrap(stateBytes: ByteArray): UnwrappedSaveState {
        if (stateBytes.size < TRAILER_SIZE) {
            return UnwrappedSaveState(stateBytes, null)
        }
        val magicStart = stateBytes.size - 4
        if (stateBytes[magicStart] != MAGIC[0] ||
            stateBytes[magicStart + 1] != MAGIC[1] ||
            stateBytes[magicStart + 2] != MAGIC[2] ||
            stateBytes[magicStart + 3] != MAGIC[3]
        ) {
            return UnwrappedSaveState(stateBytes, null)
        }
        val bb =
            ByteBuffer
                .wrap(stateBytes, stateBytes.size - TRAILER_SIZE, TRAILER_SIZE)
                .order(ByteOrder.BIG_ENDIAN)
        val progressLen = bb.getInt()
        val version = bb.getShort()
        if (version != VERSION || progressLen <= 0 || progressLen > stateBytes.size - TRAILER_SIZE) {
            return UnwrappedSaveState(stateBytes, null)
        }
        val coreLen = stateBytes.size - TRAILER_SIZE - progressLen
        return UnwrappedSaveState(
            coreState = stateBytes.copyOfRange(0, coreLen),
            raProgress = stateBytes.copyOfRange(coreLen, coreLen + progressLen),
        )
    }
}
