package com.omnidroid.lib.library.scan

import android.net.Uri
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.storage.BaseStorageFile
import com.omnidroid.lib.storage.GroupedStorageFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LibraryDiffTest {
    @Test
    fun unchangedWhenUriSizeMtimeMatch() {
        val file = file("a.nes", 100, 1_000)
        val game = game(1, file, "a.nes")
        val result =
            LibraryDiff.compute(listOf(group(file)), listOf(game)) { null }
        assertEquals(1, result.size)
        assertTrue(result.single() is LibraryDiffEntry.Unchanged)
    }

    @Test
    fun modifiedWhenSizeChanges() {
        val file = file("a.nes", 200, 1_000)
        val game = game(1, file("a.nes", 100, 1_000), "a.nes")
        val result =
            LibraryDiff.compute(listOf(group(file)), listOf(game)) { null }
        assertTrue(result.single() is LibraryDiffEntry.Modified)
    }

    @Test
    fun addedAndRemovedWhenDifferentFiles() {
        val kept = file("keep.nes", 10, 1)
        val added = file("new.nes", 20, 2)
        val removedGame = game(2, file("gone.nes", 30, 3), "gone.nes")
        val result =
            LibraryDiff.compute(
                listOf(group(kept), group(added)),
                listOf(game(1, kept, "keep.nes"), removedGame),
            ) { null }
        assertTrue(result.any { it is LibraryDiffEntry.Added })
        assertTrue(result.any { it is LibraryDiffEntry.Removed })
        assertTrue(result.any { it is LibraryDiffEntry.Unchanged })
    }

    @Test
    fun renamesWhenUniqueSizeMtime() {
        val oldFile = file("old.nes", 50, 9)
        val newFile = file("new.nes", 50, 9)
        val game = game(1, oldFile, "old.nes")
        val result =
            LibraryDiff.compute(listOf(group(newFile)), listOf(game)) { null }
        val renamed = result.filterIsInstance<LibraryDiffEntry.Renamed>().single()
        assertEquals(1, renamed.game.id)
        assertEquals("new.nes", renamed.group.primaryFile.name)
    }

    @Test
    fun ambiguousRenameUsesPartialHash() {
        val oldA = file("a.nes", 50, 9)
        val oldB = file("b.nes", 50, 9)
        val newA = file("a2.nes", 50, 9)
        val newB = file("b2.nes", 50, 9)
        val gameA = game(1, oldA, "a.nes").copy(partialHash = "hash-a")
        val gameB = game(2, oldB, "b.nes").copy(partialHash = "hash-b")
        val hashes =
            mapOf(
                newA.uri.toString() to "hash-a",
                newB.uri.toString() to "hash-b",
            )
        val result =
            LibraryDiff.compute(
                listOf(group(newA), group(newB)),
                listOf(gameA, gameB),
            ) { hashes[it.uri.toString()] }
        val renames = result.filterIsInstance<LibraryDiffEntry.Renamed>()
        assertEquals(2, renames.size)
        assertEquals(1, renames.first { it.group.primaryFile.name == "a2.nes" }.game.id)
        assertEquals(2, renames.first { it.group.primaryFile.name == "b2.nes" }.game.id)
    }

    @Test
    fun ambiguousWithoutHashBecomesRemoveAndAdd() {
        val oldA = file("a.nes", 50, 9)
        val oldB = file("b.nes", 50, 9)
        val newA = file("a2.nes", 50, 9)
        val newB = file("b2.nes", 50, 9)
        val result =
            LibraryDiff.compute(
                listOf(group(newA), group(newB)),
                listOf(game(1, oldA, "a.nes"), game(2, oldB, "b.nes")),
            ) { "same" }
        assertEquals(0, result.filterIsInstance<LibraryDiffEntry.Renamed>().size)
        assertEquals(2, result.filterIsInstance<LibraryDiffEntry.Added>().size)
        assertEquals(2, result.filterIsInstance<LibraryDiffEntry.Removed>().size)
    }

    private fun group(file: BaseStorageFile) = GroupedStorageFiles(file, emptyList())

    private fun file(
        name: String,
        size: Long,
        mtime: Long,
    ) = BaseStorageFile(
        name = name,
        size = size,
        uri = Uri.parse("content://tree/doc/$name"),
        lastModified = mtime,
    )

    private fun game(
        id: Int,
        file: BaseStorageFile,
        fileName: String,
    ) = Game(
        id = id,
        fileName = fileName,
        fileUri = file.uri.toString(),
        title = fileName,
        systemId = "nes",
        developer = null,
        coverFrontUrl = null,
        lastIndexedAt = 1L,
        fileSize = file.size,
        fileLastModified = file.lastModified,
    )
}
