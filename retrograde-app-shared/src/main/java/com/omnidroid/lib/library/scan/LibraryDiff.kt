package com.omnidroid.lib.library.scan

import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.storage.BaseStorageFile
import com.omnidroid.lib.storage.GroupedStorageFiles

sealed class LibraryDiffEntry {
    data class Unchanged(
        val game: Game,
        val group: GroupedStorageFiles,
    ) : LibraryDiffEntry()

    data class Modified(
        val game: Game,
        val group: GroupedStorageFiles,
    ) : LibraryDiffEntry()

    data class Added(
        val group: GroupedStorageFiles,
    ) : LibraryDiffEntry()

    data class Removed(
        val game: Game,
    ) : LibraryDiffEntry()

    data class Renamed(
        val game: Game,
        val group: GroupedStorageFiles,
    ) : LibraryDiffEntry()
}

object LibraryDiff {
    data class SizeMtime(val size: Long, val lastModified: Long)

    fun compute(
        groups: List<GroupedStorageFiles>,
        existingGames: List<Game>,
        resolveHash: (BaseStorageFile) -> String?,
    ): List<LibraryDiffEntry> {
        val byUri = existingGames.associateBy { it.fileUri }
        val matchedGameIds = mutableSetOf<Int>()
        val unchangedOrModified = mutableListOf<LibraryDiffEntry>()
        val unmatchedGroups = mutableListOf<GroupedStorageFiles>()

        for (group in groups) {
            val primary = group.primaryFile
            val existing = byUri[primary.uri.toString()]
            if (existing != null) {
                matchedGameIds += existing.id
                val sizeMatch = existing.fileSize == null || existing.fileSize == primary.size
                val mtimeMatch =
                    existing.fileLastModified == null || existing.fileLastModified == primary.lastModified
                if (sizeMatch && mtimeMatch) {
                    unchangedOrModified += LibraryDiffEntry.Unchanged(existing, group)
                } else {
                    unchangedOrModified += LibraryDiffEntry.Modified(existing, group)
                }
            } else {
                unmatchedGroups += group
            }
        }

        val removedCandidates = existingGames.filter { it.id !in matchedGameIds }
        val (renames, leftoverAdded, leftoverRemoved) =
            matchRenames(removedCandidates, unmatchedGroups, resolveHash)

        return buildList {
            addAll(unchangedOrModified)
            addAll(renames)
            leftoverAdded.forEach { add(LibraryDiffEntry.Added(it)) }
            leftoverRemoved.forEach { add(LibraryDiffEntry.Removed(it)) }
        }
    }

    private fun matchRenames(
        removed: List<Game>,
        added: List<GroupedStorageFiles>,
        resolveHash: (BaseStorageFile) -> String?,
    ): Triple<List<LibraryDiffEntry.Renamed>, List<GroupedStorageFiles>, List<Game>> {
        if (removed.isEmpty() || added.isEmpty()) {
            return Triple(emptyList(), added, removed)
        }

        val removedByKey =
            removed.groupBy { game ->
                val size = game.fileSize
                val mtime = game.fileLastModified
                if (size == null || mtime == null) null else SizeMtime(size, mtime)
            }
        val addedByKey =
            added.groupBy { group ->
                SizeMtime(group.primaryFile.size, group.primaryFile.lastModified)
            }

        val renames = mutableListOf<LibraryDiffEntry.Renamed>()
        val usedRemoved = mutableSetOf<Int>()
        val usedAdded = mutableSetOf<String>()

        for ((key, addedGroups) in addedByKey) {
            val removedGames = removedByKey[key].orEmpty().filter { it.id !in usedRemoved }
            if (removedGames.isEmpty()) continue

            if (removedGames.size == 1 && addedGroups.size == 1) {
                val game = removedGames.first()
                val group = addedGroups.first()
                renames += LibraryDiffEntry.Renamed(game, group)
                usedRemoved += game.id
                usedAdded += group.primaryFile.uri.toString()
                continue
            }

            // Ambiguous size+mtime: match via partial hash. Removed path is gone, so only
            // games that already stored partialHash can be matched to a hashed added file.
            for (group in addedGroups) {
                val uri = group.primaryFile.uri.toString()
                if (uri in usedAdded) continue
                val hash = resolveHash(group.primaryFile) ?: continue
                val matches =
                    removedGames.filter { game ->
                        game.id !in usedRemoved && game.partialHash != null && game.partialHash == hash
                    }
                if (matches.size == 1) {
                    val game = matches.first()
                    renames += LibraryDiffEntry.Renamed(game, group)
                    usedRemoved += game.id
                    usedAdded += uri
                }
            }
        }

        val leftoverAdded = added.filter { it.primaryFile.uri.toString() !in usedAdded }
        val leftoverRemoved = removed.filter { it.id !in usedRemoved }
        return Triple(renames, leftoverAdded, leftoverRemoved)
    }
}
