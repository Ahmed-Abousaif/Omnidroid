package com.omnidroid.app.mobile.shared.controller

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.lib.library.db.entity.Game

fun Modifier.controllerFocusGlow(
    shape: Shape = RoundedCornerShape(12.dp),
    strokeWidth: Dp = 1.25.dp,
    glowWidth: Dp = 5.dp,
    intensity: Float = 1f,
): Modifier =
    composed {
        var focused by remember { mutableStateOf(false) }
        val progress by animateFloatAsState(
            targetValue = if (focused) 1f else 0f,
            label = "controllerFocus",
        )
        val glowColor = LibraryNeonGreen

        this
            .onFocusChanged { focused = it.isFocused }
            .drawControllerGlow(
                progress = progress * intensity,
                color = glowColor,
                shape = shape,
                strokeWidth = strokeWidth,
                glowWidth = glowWidth,
            )
    }

fun Modifier.reportControllerGame(game: Game): Modifier =
    composed {
        val navigation = LocalControllerNavigation.current
        onFocusChanged { focusState ->
            if (navigation == null) return@onFocusChanged
            if (focusState.isFocused) {
                navigation.setFocusedGame(game)
            } else if (navigation.focusedGame.value?.id == game.id) {
                navigation.setFocusedGame(null)
            }
        }
    }

private fun Modifier.drawControllerGlow(
    progress: Float,
    color: Color,
    shape: Shape,
    strokeWidth: Dp,
    glowWidth: Dp,
): Modifier {
    if (progress <= 0f) return this
    return drawWithContent {
        drawContent()
        val radius =
            when (shape) {
                is RoundedCornerShape -> {
                    val maxRadius = size.minDimension / 2f
                    shape.topStart.toPx(size, this).coerceAtMost(maxRadius)
                }
                else -> size.minDimension / 2f
            }
        val corner = CornerRadius(radius, radius)
        drawRoundRect(
            color = color.copy(alpha = 0.22f * progress),
            cornerRadius = corner,
            style = Stroke(width = glowWidth.toPx()),
        )
        drawRoundRect(
            color = color.copy(alpha = progress),
            cornerRadius = corner,
            style = Stroke(width = strokeWidth.toPx()),
        )
    }
}
