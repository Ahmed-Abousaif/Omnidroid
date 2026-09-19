package com.omnidroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.lib.library.db.entity.Game

@Composable
fun OmnidroidGameImage(
    modifier: Modifier = Modifier,
    game: Game,
    aspectRatio: Float? = LibraryGameCardAspectRatio,
) {
    val fallbackDrawable =
        remember(game) {
            CoverUtils.getFallbackDrawable(game)
        }

    val fallbackPainter = rememberDrawablePainter(drawable = fallbackDrawable)

    AsyncImage(
        model =
            CoverUtils.coverRequest(LocalContext.current, game),
        contentDescription = game.displayName,
        modifier =
            modifier
                .then(
                    if (aspectRatio != null) {
                        Modifier.aspectRatio(aspectRatio)
                    } else {
                        Modifier
                    },
                ),
        fallback = fallbackPainter,
        error = fallbackPainter,
        contentScale = ContentScale.Crop,
    )
}
