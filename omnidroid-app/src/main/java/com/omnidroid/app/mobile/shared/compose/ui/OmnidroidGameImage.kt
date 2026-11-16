package com.omnidroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.size.Size
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.app.shared.covers.backdropRequest
import com.omnidroid.lib.library.db.entity.Game
import kotlin.math.abs

/**
 * Whether a cover of [imageAspect] should be shown whole over a blurred fill instead of cropped
 * to [cardAspect]. Covers within [CROP_TOLERANCE] of the card shape still crop, so ordinary box
 * art fills the card edge to edge.
 */
internal fun shouldLetterboxCover(
    imageAspect: Float,
    cardAspect: Float,
): Boolean = imageAspect > 0f && cardAspect > 0f && abs(imageAspect / cardAspect - 1f) > CROP_TOLERANCE

private const val CROP_TOLERANCE = 0.15f

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

    // Cropped cards adapt to the cover's shape; callers asking for another scale get exactly that.
    // Free-size cards (aspectRatio == null) compare against their measured size.
    var imageAspect by remember(request) { mutableStateOf<Float?>(null) }
    var measuredAspect by remember { mutableStateOf<Float?>(null) }
    val cardAspect = aspectRatio ?: measuredAspect
    val letterbox =
        contentScale == ContentScale.Crop &&
            cardAspect != null &&
            imageAspect?.let { shouldLetterboxCover(it, cardAspect) } == true

    Box(
        modifier =
            modifier.then(
                if (aspectRatio != null) {
                    Modifier.aspectRatio(aspectRatio)
                } else {
                    Modifier.onSizeChanged { size ->
                        if (size.width > 0 && size.height > 0) measuredAspect = size.width.toFloat() / size.height
                    }
                },
            ),
    ) {
        if (letterbox) {
            val backdrop = remember(request) { backdropRequest(context, game, BACKDROP_WIDTH, BACKDROP_HEIGHT, BACKDROP_RADIUS) }
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.25f)))
        }
        AsyncImage(
            model = request,
            contentDescription = game.displayName,
            modifier = Modifier.matchParentSize(),
            fallback = fallbackPainter,
            error = fallbackPainter,
            contentScale = if (letterbox) ContentScale.Fit else contentScale,
            onSuccess = { state ->
                val size = state.painter.intrinsicSize
                if (size.width > 0f && size.height > 0f) {
                    imageAspect = size.width / size.height
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
}

private const val BACKDROP_WIDTH = 24
private const val BACKDROP_HEIGHT = 36
private const val BACKDROP_RADIUS = 6
