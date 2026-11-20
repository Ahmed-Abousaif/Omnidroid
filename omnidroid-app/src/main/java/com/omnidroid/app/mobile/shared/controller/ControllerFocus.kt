package com.omnidroid.app.mobile.shared.controller

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnidroid.app.mobile.shared.compose.ui.RingPlacement
import com.omnidroid.app.mobile.shared.compose.ui.drawShapeRing
import com.omnidroid.app.mobile.shared.compose.ui.libraryAccentColor
import com.omnidroid.lib.library.db.entity.Game

/**
 * Controller / D-pad focus ring: a crisp ring in the accent colour, drawn
 * just inside the element, so lists, cards and docks that clip their children never cut it. A thin
 * dark line inside the ring keeps it readable on dark or light fills. Pass [glowColor] to draw it in
 * another colour, e.g. the on-accent colour for an element filled with the accent.
 */
fun Modifier.controllerFocusGlow(
    shape: Shape = RoundedCornerShape(12.dp),
    strokeWidth: Dp = 2.dp,
    inset: Dp = 2.dp,
    intensity: Float = 1f,
    glowColor: Color? = null,
): Modifier =
    composed {
        var focused by remember { mutableStateOf(false) }
        val progress by animateFloatAsState(
            targetValue = if (focused) 1f else 0f,
            animationSpec = tween(120),
            label = "controllerFocus",
        )
        val color = glowColor ?: libraryAccentColor
        this
            .onFocusChanged { focused = it.isFocused }
            .drawWithContent {
                drawContent()
                val alpha = progress * intensity
                if (alpha <= 0f) return@drawWithContent
                drawShapeRing(
                    shape = shape,
                    color = color.copy(alpha = alpha),
                    width = strokeWidth.toPx(),
                    gap = inset.toPx(),
                    placement = RingPlacement.Inside,
                    innerLine = Color.Black.copy(alpha = 0.55f * alpha),
                )
            }
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
