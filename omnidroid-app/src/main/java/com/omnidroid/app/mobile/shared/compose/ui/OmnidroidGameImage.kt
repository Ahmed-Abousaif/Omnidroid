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
    contentScale: ContentScale = ContentScale.Crop,
    onAspectRatio: ((Float) -> Unit)? = null,
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
        contentScale = contentScale,
        onSuccess = { state ->
            val size = state.painter.intrinsicSize
            if (size.width > 0f && size.height > 0f) {
                onAspectRatio?.invoke(size.width / size.height)
            }
        },
        onError = {
            val size = fallbackPainter.intrinsicSize
            if (size.width > 0f && size.height > 0f) {
                onAspectRatio?.invoke(size.width / size.height)
            } else {
                onAspectRatio?.invoke(1f)
            }
        },
    )
}
