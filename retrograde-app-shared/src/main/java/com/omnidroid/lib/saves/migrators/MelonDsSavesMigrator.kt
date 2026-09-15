package com.omnidroid.lib.saves.migrators

import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.storage.DirectoriesManager
import java.io.File

object MelonDsSavesMigrator : SavesMigrator {
    override fun loadPreviousSaveForGame(
        game: Game,
        directoriesManager: DirectoriesManager,
    ): ByteArray? {
        val savesDirectory = directoriesManager.getSavesDirectory()
        val previousSaveFileName = "${game.fileName.substringBeforeLast(".")}.sav"
        val previousSaveFile = File(savesDirectory, previousSaveFileName)

        return if (previousSaveFile.exists() && previousSaveFile.length() > 0) {
            previousSaveFile.readBytes()
        } else {
            null
        }
    }
}
