package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlin.math.roundToInt

@Composable
fun GameHeroCover(
    game: Game,
    from: Rect,
    to: Rect,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    if (from.width <= 1f || from.height <= 1f) return
    val dest = if (to.width > 1f && to.height > 1f) to else from
    val rect = lerp(from, dest, progress)
    val density = LocalDensity.current
    val radius = androidx.compose.ui.unit.lerp(4.dp, 16.dp, progress)
    Box(
        modifier =
            modifier
                .zIndex(8f)
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(
                    width = with(density) { rect.width.toDp() },
                    height = with(density) { rect.height.toDp() },
                )
                .clip(RoundedCornerShape(radius)),
    ) {
        LemuroidGameImage(
            modifier = Modifier.fillMaxSize(),
            game = game,
            aspectRatio = null,
        )
    }
}
