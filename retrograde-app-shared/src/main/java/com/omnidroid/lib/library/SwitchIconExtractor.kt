package com.omnidroid.lib.library

interface SwitchIconExtractor {
    /**
     * Extracts the icon embedded in the Switch ROM at [fileUri] and saves it as the custom cover
     * for [gameId] using the same pipeline as user-chosen covers.
     *
     * @return the `customCoverPath` string (absolute path + cache-busting `?mtime` suffix) on
     *         success, or `null` if the icon could not be extracted or saved.
     */
    suspend fun extractAndSave(gameId: Int, fileUri: String): String?
}

