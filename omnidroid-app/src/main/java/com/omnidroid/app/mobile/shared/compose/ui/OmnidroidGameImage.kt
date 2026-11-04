package com.omnidroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.size.Size
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.lib.library.db.entity.Game

@Composable
fun OmnidroidGameImage(
    modifier: Modifier = Modifier,
    game: Game,
    aspectRatio: Float? = LibraryGameCardAspectRatio,
    contentScale: ContentScale = ContentScale.Crop,
    decodeWidth: Dp = 160.dp,
    onAspectRatio: ((Float) -> Unit)? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val decodePx = density.run { decodeWidth.roundToPx() }
    val fallbackDrawable =
        remember(game) {
            CoverUtils.getFallbackDrawable(game)
        }

    val fallbackPainter = rememberDrawablePainter(drawable = fallbackDrawable)
    val request =
        remember(game.id, game.coverFrontUrl, game.customCoverPath, decodePx) {
            CoverUtils.coverRequest(
                context,
                game,
                size = Size(decodePx, (decodePx / (aspectRatio ?: LibraryGameCardAspectRatio)).toInt()),
            )
        }

    AsyncImage(
        model = request,
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
