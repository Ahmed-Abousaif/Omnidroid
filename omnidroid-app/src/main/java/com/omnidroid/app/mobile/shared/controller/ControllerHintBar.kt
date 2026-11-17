package com.omnidroid.app.mobile.shared.controller

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ControllerHintBarRowHeight = 32.dp
private val HintKeyFill = Color(0xFF2A2A33)
private val HintKeyBorder = Color(0x66FFFFFF)
private val HintLabel = Color(0xD9FFFFFF)
private val HintGap = 12.dp
private val HintClusterGap = 16.dp

@Composable
fun ControllerHintBar(
    visible: Boolean,
    hints: ControllerHints,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(ControllerHintBarRowHeight)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black,
                            0.45f to Color.Black.copy(alpha = 0.82f),
                            1f to Color.Transparent,
                        ),
                    )
                    .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            HintRow(left = hints.left, right = hints.right)
        }
    }
}

/**
 * Left hints from the start, right hints from the end. When large text or a narrow screen leaves no
 * room for all of them, whole hints are dropped (see [fitControllerHints]) instead of the last ones
 * being clipped mid-word off the edge.
 */
@Composable
private fun HintRow(
    left: List<ControllerHint>,
    right: List<ControllerHint>,
) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            left.forEach { HintItem(it) }
            right.forEach { HintItem(it) }
        },
    ) { measurables, constraints ->
        val gap = HintGap.roundToPx()
        val clusterGap = HintClusterGap.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val leftPlaceables = placeables.take(left.size)
        val rightPlaceables = placeables.drop(left.size)
        val (keepLeft, keepRight) =
            fitControllerHints(
                leftWidths = leftPlaceables.map { it.width },
                rightWidths = rightPlaceables.map { it.width },
                gap = gap,
                clusterGap = clusterGap,
                maxWidth = constraints.maxWidth,
            )
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            var x = 0
            leftPlaceables.take(keepLeft).forEach {
                it.place(x, (height - it.height) / 2)
                x += it.width + gap
            }
            x = constraints.maxWidth
            rightPlaceables.take(keepRight).asReversed().forEach {
                x -= it.width
                it.place(x, (height - it.height) / 2)
                x -= gap
            }
        }
    }
}

/**
 * How many leading left and right hints fit in [maxWidth]. Drops from the end of the left cluster
 * first (keeping its first hint), then from the end of the right cluster, then the rest.
 */
internal fun fitControllerHints(
    leftWidths: List<Int>,
    rightWidths: List<Int>,
    gap: Int,
    clusterGap: Int,
    maxWidth: Int,
): Pair<Int, Int> {
    fun span(widths: List<Int>, count: Int) = widths.take(count).sum() + gap * (count - 1).coerceAtLeast(0)
    var keepLeft = leftWidths.size
    var keepRight = rightWidths.size
    while (keepLeft + keepRight > 0) {
        val between = if (keepLeft > 0 && keepRight > 0) clusterGap else 0
        if (span(leftWidths, keepLeft) + between + span(rightWidths, keepRight) <= maxWidth) break
        when {
            keepLeft > 1 -> keepLeft--
            keepRight > 1 -> keepRight--
            keepLeft > 0 -> keepLeft--
            else -> keepRight--
        }
    }
    return keepLeft to keepRight
}

@Composable
private fun HintItem(hint: ControllerHint) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        hint.keys.forEach { key ->
            HintKeyChip(label = key)
        }
        Text(
            text = hint.label,
            color = HintLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun HintKeyChip(label: String) {
    val pill = label.length > 1
    Box(
        modifier =
            if (pill) {
                Modifier
                    .height(18.dp)
                    .defaultMinSize(minWidth = 24.dp)
                    .background(HintKeyFill, RoundedCornerShape(9.dp))
                    .border(1.dp, HintKeyBorder, RoundedCornerShape(9.dp))
                    .padding(horizontal = 6.dp)
            } else {
                Modifier
                    .size(18.dp)
                    .background(HintKeyFill, CircleShape)
                    .border(1.dp, HintKeyBorder, CircleShape)
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            style =
                TextStyle(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 9.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
        )
    }
}
