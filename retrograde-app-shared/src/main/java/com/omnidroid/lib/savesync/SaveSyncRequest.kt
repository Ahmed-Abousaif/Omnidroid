package com.omnidroid.lib.savesync

import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.SystemID

data class SaveSyncRequest(
    val cores: Set<CoreID> = emptySet(),
    val gameFileName: String? = null,
    val includeSaves: Boolean = true,
    val includeStates: Boolean = true,
    val includePreviews: Boolean = true,
    val excludedFileNames: Set<String> = emptySet(),
    val alwaysFileNames: Set<String> = emptySet(),
    /** System of the game in a per-game sync ([gameFileName]), as its database name. */
    val gameSystemId: String? = null,
) {
    /**
     * A PS2 memory card isn't named after any game: it holds the saves of every PS2 game, so a
     * per-game sync of a PS2 game carries the cards whatever the save-state settings say.
     */
    val includesPs2MemoryCards: Boolean
        get() = gameFileName != null && gameSystemId == SystemID.PS2.dbname
}
