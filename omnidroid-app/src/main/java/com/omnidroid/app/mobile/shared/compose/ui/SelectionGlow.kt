package com.omnidroid.app.mobile.shared.compose.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

private val GlowSpread = 14.dp
private const val GLOW_LAYERS = 14
private const val GLOW_LAYER_ALPHA = 0.016f

/** Gentle accent halo and outline that fade in as a game becomes selected. */
fun Modifier.selectionGlow(
    accent: Color,
    corner: Dp,
    amount: () -> Float,
): Modifier =
    drawBehind {
        val selected = amount()
        if (selected <= 0.01f) return@drawBehind
        val strength = selected * selected
        val spread = GlowSpread.toPx()
        val radius = corner.toPx()
        for (layer in GLOW_LAYERS downTo 1) {
            val grow = spread * layer / GLOW_LAYERS
            drawRoundRect(
                color = accent.copy(alpha = GLOW_LAYER_ALPHA * strength),
                topLeft = Offset(-grow, -grow),
                size = Size(size.width + grow * 2, size.height + grow * 2),
                cornerRadius = CornerRadius(radius + grow),
            )
        }
    }.drawWithContent {
        drawContent()
        val selected = amount()
        if (selected <= 0.01f) return@drawWithContent
        val stroke = 1.5.dp.toPx()
        drawRoundRect(
            color = accent.copy(alpha = selected),
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(corner.toPx() - stroke / 2),
            style = Stroke(width = stroke),
        )
    }

/**
 * Controller focus on a game card or row, drawn like the selected game in the Console view. The
 * focused one draws above its neighbours so the halo isn't covered by the next card.
 */
fun Modifier.gameFocusGlow(corner: Dp): Modifier =
    composed {
        var focused by remember { mutableStateOf(false) }
        val progress by animateFloatAsState(
            targetValue = if (focused) 1f else 0f,
            animationSpec = tween(180),
            label = "gameFocus",
        )
        this
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged { focused = it.isFocused }
            .selectionGlow(libraryAccentColor, corner) { progress }
    }
