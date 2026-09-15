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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ControllerHintBarRowHeight = 32.dp
private val HintKeyFill = Color(0xFF2A2A33)
private val HintKeyBorder = Color(0x66FFFFFF)
private val HintLabel = Color(0xD9FFFFFF)

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HintCluster(hints = hints.left)
                Box(modifier = Modifier.weight(1f))
                HintCluster(hints = hints.right)
            }
        }
    }
}

@Composable
private fun HintCluster(hints: List<ControllerHint>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        hints.forEach { hint ->
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
                )
            }
        }
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
