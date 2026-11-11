package com.omnidroid.app.mobile.feature.addconsole

import com.omnidroid.app.mobile.feature.addconsole.AddConsoleViewModel.ConsoleItem
import com.omnidroid.lib.library.MetaSystemID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleManagementTest {
    private fun console(id: MetaSystemID = MetaSystemID.NES) =
        ConsoleItem(
            metaSystemID = id,
            registeredOrHasGames = false,
            coreInstalled = false,
            coreBundled = false,
            coreDisplayName = "Example emulator",
            databaseInstalled = false,
            databaseGameCount = 0,
            libraryGameCount = 0,
        )

    @Test
    fun layoutUsesAvailableSpaceRatherThanOrientationAlone() {
        assertFalse(useConsoleSplitLayout(360f, 640f))
        assertFalse(useConsoleSplitLayout(480f, 480f))
        assertFalse(useConsoleSplitLayout(640f, 640f))
        assertTrue(useConsoleSplitLayout(640f, 360f))
        assertTrue(useConsoleSplitLayout(720f, 720f))
        assertTrue(useConsoleSplitLayout(1280f, 800f))
        assertFalse(useConsoleSplitLayout(320f, 200f))
    }

    @Test
    fun selectionSurvivesReorderingAndFallsBackWhenFilteredOut() {
        val nes = console()
        val snes = console(MetaSystemID.SNES)
        assertEquals(MetaSystemID.NES, resolveConsoleSelection(MetaSystemID.NES, listOf(snes, nes)))
        assertEquals(MetaSystemID.SNES, resolveConsoleSelection(MetaSystemID.NES, listOf(snes)))
        assertNull(resolveConsoleSelection(MetaSystemID.NES, emptyList()))
    }

    @Test
    fun partialDatabaseIsManageableAndNeedsAttention() {
        val partial = console().copy(installedDatabaseCount = 1, expectedDatabaseCount = 2)
        assertTrue(partial.hasDatabase)
        assertTrue(partial.canRemove)
        assertTrue(partial.needsAttention)
        assertEquals(
            listOf(partial),
            consoleCatalogue(listOf(partial), "") {
                it.name
            },
        )
    }

    @Test
    fun gamesWithoutEmulatorNeedRepair() {
        assertTrue(console().copy(registeredOrHasGames = true, libraryGameCount = 5).needsAttention)
        assertFalse(console().needsAttention)
        assertFalse(console().copy(coreInstalled = true, coreBundled = true).needsAttention)
    }

    @Test
    fun builtInEmulatorAloneDoesNotPretendConsoleIsInLibrary() {
        val builtIn = console().copy(coreInstalled = true, coreBundled = true)
        assertFalse(builtIn.canRemove)
        assertFalse(builtIn.registeredOrHasGames)
    }

    @Test
    fun noDatabaseAvailableDoesNotFlagHealthyConsole() {
        assertFalse(
            console().copy(registeredOrHasGames = true, coreInstalled = true, expectedDatabaseCount = 0).needsAttention,
        )
    }

    @Test
    fun searchMatchesEmulatorAndNameIgnoringCaseAndSurroundingSpaces() {
        val nes = console()
        val snes = console(MetaSystemID.SNES).copy(coreDisplayName = "Other")
        assertEquals(
            listOf(
                nes,
            ),
            consoleCatalogue(listOf(nes, snes), " EXAMPLE ") {
                it.name
            },
        )
        assertEquals(
            listOf(snes),
            consoleCatalogue(listOf(nes, snes), "snes") { it.name },
        )
    }

    @Test
    fun attentionComesFirstThenLibraryThenRemainingSystemsByName() {
        val needsRepair = console().copy(registeredOrHasGames = true, libraryGameCount = 10)
        val partialDatabase = console(MetaSystemID.GENESIS).copy(installedDatabaseCount = 1)
        val inLibrary =
            console(MetaSystemID.SNES).copy(
                registeredOrHasGames = true,
                coreInstalled = true,
                databaseInstalled = true,
            )
        val available = console(MetaSystemID.GB)
        val builtIn = console(MetaSystemID.GBA).copy(coreInstalled = true, coreBundled = true)
        assertEquals(
            listOf(partialDatabase, needsRepair, inLibrary, available, builtIn),
            consoleCatalogue(listOf(builtIn, inLibrary, needsRepair, available, partialDatabase), "") { it.name },
        )
    }
}
