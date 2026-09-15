package com.omnidroid.app.shared.deeplink

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.omnidroid.lib.library.db.entity.Game

object DeepLink {
    fun openLeanbackUri(appContext: Context): Uri {
        return Uri.parse("omnidroid://${appContext.packageName}/open-leanback")
    }

    private fun uriForGame(
        appContext: Context,
        game: Game,
    ): Uri {
        return Uri.parse("omnidroid://${appContext.packageName}/play-game/id/${game.id}")
    }

    fun launchIntentForGame(
        appContext: Context,
        game: Game,
    ) = Intent(Intent.ACTION_VIEW, uriForGame(appContext, game))
}
