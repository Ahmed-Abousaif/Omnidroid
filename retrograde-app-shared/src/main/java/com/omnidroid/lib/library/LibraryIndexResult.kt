package com.omnidroid.lib.library

data class LibraryIndexResult(
    val added: Int = 0,
    val modified: Int = 0,
    val removed: Int = 0,
    val renamed: Int = 0,
    val unchanged: Int = 0,
    val folderReadable: Boolean = true,
    val newSystemIds: Set<String> = emptySet(),
) {
    val deltaCount: Int get() = added + modified + removed + renamed

    companion object {
        val EMPTY = LibraryIndexResult()
    }
}
