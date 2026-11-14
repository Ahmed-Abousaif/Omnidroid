package com.omnidroid.lib.game

class GameLoaderException(val error: GameLoaderError) : RuntimeException("Game Loader error: $error")

sealed class GameLoaderError {
    object GLIncompatible : GameLoaderError()

    object Generic : GameLoaderError()

    object LoadCore : GameLoaderError()

    object LoadGame : GameLoaderError()

    data class MissingAssets(val coreID: com.omnidroid.lib.library.CoreID) : GameLoaderError()

    object CoreUninstalled : GameLoaderError()

    object Saves : GameLoaderError()

    object UnsupportedArchitecture : GameLoaderError()

    data class MissingBiosFiles(val missingFiles: List<String>) : GameLoaderError()
}
