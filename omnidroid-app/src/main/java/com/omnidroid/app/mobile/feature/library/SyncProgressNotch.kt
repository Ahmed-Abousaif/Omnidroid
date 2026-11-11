package com.omnidroid.app.mobile.feature.library

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.compose.ui.LocalAdaptiveLayout
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.lib.core.SyncProgress

enum class NotchOrientation {
    TOP, // Curves into top anchor (Search bar in portrait)
    BOTTOM, // Curves into bottom screen edge (Landscape)
}

class NotchShape(
    private val orientation: NotchOrientation,
    private val flareRadius: Dp,
    private val cornerRadius: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val rf = with(density) { flareRadius.toPx() }
        val rc = with(density) { cornerRadius.toPx() }
        val path = createNotchPath(size.width, size.height, rf, rc, orientation)
        return Outline.Generic(path)
    }
}

/**
 * Builds the closed path for the notch fill and clipping.
 */
internal fun createNotchPath(
    w: Float,
    h: Float,
    rf: Float,
    rc: Float,
    orientation: NotchOrientation,
): Path {
    val path = Path()
    if (w <= 0f || h <= 0f) return path

    val effectiveRf = rf.coerceAtMost(w / 4f).coerceAtMost(h / 2f)
    val effectiveRc = rc.coerceAtMost((w - 2f * effectiveRf) / 2f).coerceAtMost(h - effectiveRf)
    val c = 0.55228475f

    if (orientation == NotchOrientation.BOTTOM) {
        // Notch is at bottom of screen, pointing UP. Base sits against screen bottom (y = h).
        path.moveTo(0f, h)
        // Concave fillet curving up from screen edge into left wall
        path.cubicTo(
            effectiveRf * c, h,
            effectiveRf, h - effectiveRf * (1f - c),
            effectiveRf, h - effectiveRf,
        )
        // Vertical left wall
        path.lineTo(effectiveRf, effectiveRc)
        // Convex top-left rounded corner
        path.cubicTo(
            effectiveRf, effectiveRc * (1f - c),
            effectiveRf + effectiveRc * (1f - c), 0f,
            effectiveRf + effectiveRc, 0f,
        )
        // Horizontal top edge
        path.lineTo(w - effectiveRf - effectiveRc, 0f)
        // Convex top-right rounded corner
        path.cubicTo(
            w - effectiveRf - effectiveRc * (1f - c), 0f,
            w - effectiveRf, effectiveRc * (1f - c),
            w - effectiveRf, effectiveRc,
        )
        // Vertical right wall
        path.lineTo(w - effectiveRf, h - effectiveRf)
        // Concave fillet curving down into screen edge
        path.cubicTo(
            w - effectiveRf, h - effectiveRf * (1f - c),
            w - effectiveRf * c, h,
            w, h,
        )
        // Close bottom base
        path.lineTo(0f, h)
        path.close()
    } else {
        // TOP: Notch comes out of search bar at top of screen, pointing DOWN. Base sits against search bar (y = 0).
        path.moveTo(0f, 0f)
        // Concave fillet curving down from search bar into left wall
        path.cubicTo(
            effectiveRf * c, 0f,
            effectiveRf, effectiveRf * (1f - c),
            effectiveRf, effectiveRf,
        )
        // Vertical left wall
        path.lineTo(effectiveRf, h - effectiveRc)
        // Convex bottom-left rounded corner
        path.cubicTo(
            effectiveRf, h - effectiveRc * (1f - c),
            effectiveRf + effectiveRc * (1f - c), h,
            effectiveRf + effectiveRc, h,
        )
        // Horizontal bottom edge
        path.lineTo(w - effectiveRf - effectiveRc, h)
        // Convex bottom-right rounded corner
        path.cubicTo(
            w - effectiveRf - effectiveRc * (1f - c), h,
            w - effectiveRf, h - effectiveRc * (1f - c),
            w - effectiveRf, h - effectiveRc,
        )
        // Vertical right wall
        path.lineTo(w - effectiveRf, effectiveRf)
        // Concave fillet curving up into search bar
        path.cubicTo(
            w - effectiveRf, effectiveRf * (1f - c),
            w - effectiveRf * c, 0f,
            w, 0f,
        )
        // Close top base
        path.lineTo(0f, 0f)
        path.close()
    }
    return path
}

/**
 * Builds the open stroke path outlining only the visible silhouette of the notch,
 * excluding the seam where it attaches to the screen edge or search bar.
 */
internal fun createNotchStrokePath(
    w: Float,
    h: Float,
    rf: Float,
    rc: Float,
    orientation: NotchOrientation,
): Path {
    val path = Path()
    if (w <= 0f || h <= 0f) return path

    val effectiveRf = rf.coerceAtMost(w / 4f).coerceAtMost(h / 2f)
    val effectiveRc = rc.coerceAtMost((w - 2f * effectiveRf) / 2f).coerceAtMost(h - effectiveRf)
    val c = 0.55228475f

    if (orientation == NotchOrientation.BOTTOM) {
        path.moveTo(0f, h)
        path.cubicTo(
            effectiveRf * c, h,
            effectiveRf, h - effectiveRf * (1f - c),
            effectiveRf, h - effectiveRf,
        )
        path.lineTo(effectiveRf, effectiveRc)
        path.cubicTo(
            effectiveRf, effectiveRc * (1f - c),
            effectiveRf + effectiveRc * (1f - c), 0f,
            effectiveRf + effectiveRc, 0f,
        )
        path.lineTo(w - effectiveRf - effectiveRc, 0f)
        path.cubicTo(
            w - effectiveRf - effectiveRc * (1f - c), 0f,
            w - effectiveRf, effectiveRc * (1f - c),
            w - effectiveRf, effectiveRc,
        )
        path.lineTo(w - effectiveRf, h - effectiveRf)
        path.cubicTo(
            w - effectiveRf, h - effectiveRf * (1f - c),
            w - effectiveRf * c, h,
            w, h,
        )
    } else {
        path.moveTo(0f, 0f)
        path.cubicTo(
            effectiveRf * c, 0f,
            effectiveRf, effectiveRf * (1f - c),
            effectiveRf, effectiveRf,
        )
        path.lineTo(effectiveRf, h - effectiveRc)
        path.cubicTo(
            effectiveRf, h - effectiveRc * (1f - c),
            effectiveRf + effectiveRc * (1f - c), h,
            effectiveRf + effectiveRc, h,
        )
        path.lineTo(w - effectiveRf - effectiveRc, h)
        path.cubicTo(
            w - effectiveRf - effectiveRc * (1f - c), h,
            w - effectiveRf, h - effectiveRc * (1f - c),
            w - effectiveRf, h - effectiveRc,
        )
        path.lineTo(w - effectiveRf, effectiveRf)
        path.cubicTo(
            w - effectiveRf, effectiveRf * (1f - c),
            w - effectiveRf * c, 0f,
            w, 0f,
        )
    }
    return path
}

@Composable
fun SyncProgressNotch(
    syncProgress: SyncProgress?,
    orientation: NotchOrientation,
    modifier: Modifier = Modifier,
) {
    val adaptiveSpec = LocalAdaptiveLayout.current
    val scale = adaptiveSpec.uiScale

    val notchHeight = (30 * scale).dp
    // Half-height radii join the flare and outer corner without a straight side wall.
    val flareRadius = notchHeight / 2
    val cornerRadius = notchHeight / 2
    val strokeWidth = (0.8f * scale).dp

    val shape = NotchShape(
        orientation = orientation,
        flareRadius = flareRadius,
        cornerRadius = cornerRadius,
    )

    val infiniteTransition = rememberInfiniteTransition(label = "notchActivity")
    val strobeAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.22f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "notchStrobeAlpha",
    )
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.70f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "notchDotAlpha",
    )

    val displayText =
        syncProgress?.stepText?.ifBlank { null }
            ?: stringResource(R.string.library_scanning)
    val hasCount = (syncProgress?.current ?: 0) > 0 && (syncProgress?.total ?: 0) > 0
    val fullText =
        if (hasCount) {
            "$displayText (${syncProgress!!.current}/${syncProgress.total})"
        } else {
            displayText
        }

    // Keep the fill identical to the console selectors and side menus, without a green tint.
    val panelBg = chromeColors.panel

    Box(
        modifier =
            modifier
                .height(notchHeight)
                .clip(shape)
                .drawBehind {
                    val rf = flareRadius.toPx()
                    val rc = cornerRadius.toPx()
                    val fillPath = createNotchPath(size.width, size.height, rf, rc, orientation)
                    val strokePath = createNotchStrokePath(size.width, size.height, rf, rc, orientation)

                    drawPath(fillPath, color = panelBg)
                    drawPath(
                        strokePath,
                        color = LibraryNeonGreen.copy(alpha = strobeAlpha),
                        style = Stroke(width = strokeWidth.toPx()),
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = (flareRadius + cornerRadius) * 0.85f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size((5.5f * scale).dp)
                        .clip(CircleShape)
                        .background(LibraryNeonGreen.copy(alpha = dotAlpha)),
            )
            Spacer(modifier = Modifier.width((6 * scale).dp))
            Text(
                text = fullText,
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = (11 * scale).sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp,
                    ),
                color = chromeColors.content.copy(alpha = 0.95f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

