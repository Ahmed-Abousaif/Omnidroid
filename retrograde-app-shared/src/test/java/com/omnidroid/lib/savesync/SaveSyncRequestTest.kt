package com.omnidroid.lib.savesync

import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.SystemID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveSyncRequestTest {
    @Test
    fun `post-game sync of a PS2 game carries the cards with default settings`() {
        // What SaveSyncWork builds after play when no core is chosen for save-state sync.
        val request = gameSync(SystemID.PS2, cores = emptySet())

        assertTrue(request.includesPs2MemoryCards)
    }

    @Test
    fun `pre-game sync of a PS2 game carries the cards`() {
        assertTrue(gameSync(SystemID.PS2, cores = setOf(CoreID.ARMSX2)).includesPs2MemoryCards)
    }

    @Test
    fun `other games and full syncs leave the cards to the saves setting`() {
        assertFalse(gameSync(SystemID.SNES, cores = emptySet()).includesPs2MemoryCards)
        assertFalse(SaveSyncRequest(cores = setOf(CoreID.ARMSX2)).includesPs2MemoryCards)
    }

    private fun gameSync(
        system: SystemID,
        cores: Set<CoreID>,
    ) = SaveSyncRequest(cores = cores, gameFileName = "Game", gameSystemId = system.dbname)
}
