package com.swordfish.lemuroid.lib.savesync

object SaveSyncFileMatcher {
    fun matchesGame(
        relativePath: String,
        gameFileName: String,
    ): Boolean {
        val fileName = relativePath.substringAfterLast('/')
        val gameBase = gameFileName.substringBeforeLast('.')
        return fileName == gameFileName ||
            fileName == "$gameBase.srm" ||
            fileName.startsWith("$gameFileName.") ||
            fileName.startsWith("$gameBase.")
    }

    fun matchesAnyGame(
        relativePath: String,
        gameFileNames: Set<String>,
    ): Boolean = gameFileNames.any { matchesGame(relativePath, it) }
}
