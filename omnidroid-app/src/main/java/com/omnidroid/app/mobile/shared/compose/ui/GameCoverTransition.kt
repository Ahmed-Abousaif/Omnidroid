package com.omnidroid.app.mobile.shared.compose.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

enum class GameCoverOrigin { Library, Continue }

/** Animate one incoming cover instead of drawing both opaque covers in the same bounds. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.gameCoverSharedBounds(
    shared: SharedTransitionScope,
    visibility: AnimatedVisibilityScope,
    gameId: Int,
    corner: Dp,
    origin: GameCoverOrigin = GameCoverOrigin.Library,
): Modifier = with(shared) {
    this@gameCoverSharedBounds.sharedElement(
        sharedContentState = rememberSharedContentState(key = "${origin.name}-game-cover-$gameId"),
        animatedVisibilityScope = visibility,
        boundsTransform = { _, _ -> tween(durationMillis = 400, easing = FastOutSlowInEasing) },
        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(corner)),
    )
}
