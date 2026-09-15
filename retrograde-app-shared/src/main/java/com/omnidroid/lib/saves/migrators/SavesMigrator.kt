package com.omnidroid.lib.saves.migrators

import com.omnidroid.lib.library.CoreID
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.storage.DirectoriesManager

interface SavesMigrator {
    fun loadPreviousSaveForGame(
        game: Game,
        directoriesManager: DirectoriesManager,
    ): ByteArray?
}

fun SystemCoreConfig.getSavesMigrator(): SavesMigrator? {
    return when (this.coreID) {
        CoreID.MELONDS -> MelonDsSavesMigrator
        else -> null
    }
}
