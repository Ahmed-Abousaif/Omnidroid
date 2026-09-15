package com.omnidroid.app.utils.games

import android.content.Context
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.db.entity.Game

class GameUtils {
    companion object {
        fun getGameSubtitle(
            context: Context,
            game: Game,
        ): String {
            val systemName = getSystemShortName(context, game)
            val developerName =
                if (game.developer?.isNotBlank() == true) {
                    "- ${game.developer}"
                } else {
                    ""
                }
            return "$systemName $developerName"
        }

        fun getSystemShortName(
            context: Context,
            game: Game,
        ): String {
            return runCatching {
                context.getString(GameSystem.findById(game.systemId).shortTitleResId)
            }.getOrDefault(game.systemId)
        }
    }
}
