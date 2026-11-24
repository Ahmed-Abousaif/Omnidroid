package com.omnidroid.lib.savesync

import android.content.Context
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.flow.map

enum class GameCloudSyncOverride {
    INHERIT,
    ALWAYS,
    NEVER,
}

class GameCloudSyncPreferences(
    private val context: Context,
) {
    private val prefs = SharedPreferencesHelper.getSharedPreferences(context)

    fun observeOverride(gameId: Int) =
        FlowSharedPreferences(prefs).getString(key(gameId), INHERIT_VALUE).asFlow().map(::decode)

    private fun decode(raw: String): GameCloudSyncOverride =
        runCatching { GameCloudSyncOverride.valueOf(raw.uppercase()) }.getOrDefault(GameCloudSyncOverride.INHERIT)

    fun getOverride(gameId: Int): GameCloudSyncOverride {
        val raw = prefs.getString(key(gameId), INHERIT_VALUE) ?: INHERIT_VALUE
        return decode(raw)
    }

    fun setOverride(
        gameId: Int,
        override: GameCloudSyncOverride,
    ) {
        prefs.edit().putString(key(gameId), override.name.lowercase()).apply()
    }

    fun partitionGames(games: List<Game>): GameSyncPartition {
        val excluded = mutableSetOf<String>()
        val always = mutableSetOf<String>()
        games.forEach { game ->
            when (getOverride(game.id)) {
                GameCloudSyncOverride.NEVER -> excluded.add(syncFileName(game))
                GameCloudSyncOverride.ALWAYS -> always.add(syncFileName(game))
                GameCloudSyncOverride.INHERIT -> Unit
            }
        }
        return GameSyncPartition(excluded, always)
    }

    fun syncFileName(game: Game): String {
        if (game.systemId != "ps3") return game.fileName
        val info =
            com.omnidroid.lib.library.scan.Ps3Content
                .read(context, android.net.Uri.parse(game.fileUri))
        return info?.let { "ps3-${it.titleId}.srm" } ?: game.fileName
    }

    fun shouldSyncGame(
        game: Game,
        globalEnabled: Boolean,
    ): Boolean =
        when (getOverride(game.id)) {
            GameCloudSyncOverride.NEVER -> false
            GameCloudSyncOverride.ALWAYS -> true
            GameCloudSyncOverride.INHERIT -> globalEnabled
        }

    private fun key(gameId: Int) = "save_sync_game_$gameId"

    data class GameSyncPartition(
        val excludedFileNames: Set<String>,
        val alwaysFileNames: Set<String>,
    )

    companion object {
        private const val INHERIT_VALUE = "inherit"
    }
}
