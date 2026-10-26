package com.omnidroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.lib.library.db.entity.Game

/** Cover shown uncropped at its own aspect ratio; size it by width. */
@Composable
fun OmnidroidSmallGameImage(
    modifier: Modifier = Modifier,
    game: Game,
) {
    val context = LocalContext.current
    val fallbackDrawable =
        remember(game) {
            CoverUtils.getFallbackDrawable(game)
        }

    val fallbackPainter = rememberDrawablePainter(fallbackDrawable)
    var aspect by remember(game.id) {
        mutableFloatStateOf(CoverUtils.getCachedCoverAspectRatio(context, game) ?: 1f)
    }

    AsyncImage(
        model =
            CoverUtils.coverRequest(context, game),
        contentDescription = game.displayName,
        modifier = modifier.aspectRatio(aspect),
        fallback = fallbackPainter,
        error = fallbackPainter,
        contentScale = ContentScale.Fit,
        onSuccess = { state ->
            val size = state.painter.intrinsicSize
            if (size.width > 0f && size.height > 0f) {
                aspect = size.width / size.height
            }
        },
    )
}
