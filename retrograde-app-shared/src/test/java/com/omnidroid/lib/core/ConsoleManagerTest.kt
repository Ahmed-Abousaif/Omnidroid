package com.omnidroid.lib.core

import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.MetaSystemID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleManagerTest {
    @Test
    fun genesisMapsToMdAndScdSlices() {
        val ids = ConsoleManager.sliceIdsFor(MetaSystemID.GENESIS)
        assertEquals(listOf("md", "scd"), ids)
    }

    @Test
    fun arcadeMapsToArcadeSlice() {
        assertEquals(listOf("arcade"), ConsoleManager.sliceIdsFor(MetaSystemID.ARCADE))
    }

    @Test
    fun systemDbNamesMatchMetaSystems() {
        assertEquals(listOf("md", "scd"), ConsoleManager.systemDbNames(MetaSystemID.GENESIS))
        assertEquals(listOf("ngp", "ngc"), ConsoleManager.systemDbNames(MetaSystemID.NGP))
        assertEquals(listOf("ws", "wsc"), ConsoleManager.systemDbNames(MetaSystemID.WS))
        assertEquals(listOf("fbneo", "mame2003plus"), ConsoleManager.systemDbNames(MetaSystemID.ARCADE))
    }

    @Test
    fun sharedGenesisCoreIsStillNeededWhenSiblingRemains() {
        val registered = setOf(MetaSystemID.SMS, MetaSystemID.GENESIS)
        assertTrue(
            ConsoleManager.isCoreStillNeeded(
                CoreID.GENESIS_PLUS_GX,
                removing = MetaSystemID.SMS,
                remainingRegistered = registered,
            ),
        )
    }

    @Test
    fun sharedGenesisCoreIsNotNeededWhenLastSiblingRemoved() {
        val registered = setOf(MetaSystemID.SMS)
        assertFalse(
            ConsoleManager.isCoreStillNeeded(
                CoreID.GENESIS_PLUS_GX,
                removing = MetaSystemID.SMS,
                remainingRegistered = registered,
            ),
        )
    }

    @Test
    fun dolphinSharedBetweenGamecubeAndWii() {
        val registered = setOf(MetaSystemID.GAMECUBE, MetaSystemID.WII)
        assertTrue(
            ConsoleManager.isCoreStillNeeded(
                CoreID.DOLPHIN,
                removing = MetaSystemID.GAMECUBE,
                remainingRegistered = registered,
            ),
        )
        assertFalse(
            ConsoleManager.isCoreStillNeeded(
                CoreID.DOLPHIN,
                removing = MetaSystemID.GAMECUBE,
                remainingRegistered = setOf(MetaSystemID.GAMECUBE),
            ),
        )
    }

    @Test
    fun exclusiveCoreIsSafeWhenOnlyOwnerRegistered() {
        val registered = setOf(MetaSystemID.NES)
        assertFalse(
            ConsoleManager.isCoreStillNeeded(
                CoreID.FCEUMM,
                removing = MetaSystemID.NES,
                remainingRegistered = registered,
            ),
        )
    }

    @Test
    fun catalogCoresForSmsIncludesGenesisPlusGx() {
        assertTrue(CoreID.GENESIS_PLUS_GX in ConsoleManager.catalogCoresFor(MetaSystemID.SMS))
    }

    @Test
    fun slicePurgeTargetsOnlyRelatedSystems() {
        // Delete-database / remove-console must only purge systems owned by the meta console.
        val smsSystems = ConsoleManager.slicesFor(MetaSystemID.SMS).flatMap { it.systems }
        assertEquals(listOf("sms"), smsSystems)

        val genesisSystems = ConsoleManager.slicesFor(MetaSystemID.GENESIS).flatMap { it.systems }
        assertEquals(listOf("md", "scd"), genesisSystems)
        assertFalse("sms" in genesisSystems)
    }

    @Test
    fun coresSafeToUninstallSkipsSharedCoreWhenSiblingRegistered() {
        // Pure mapping side of uninstall safety (bundled check needs Context, so only still-needed).
        val registered = setOf(MetaSystemID.SMS, MetaSystemID.GG, MetaSystemID.GENESIS)
        assertTrue(
            ConsoleManager.isCoreStillNeeded(
                CoreID.GENESIS_PLUS_GX,
                removing = MetaSystemID.GG,
                remainingRegistered = registered,
            ),
        )
        assertFalse(
            ConsoleManager.isCoreStillNeeded(
                CoreID.FCEUMM,
                removing = MetaSystemID.NES,
                remainingRegistered = setOf(MetaSystemID.NES, MetaSystemID.SNES),
            ),
        )
    }
}
