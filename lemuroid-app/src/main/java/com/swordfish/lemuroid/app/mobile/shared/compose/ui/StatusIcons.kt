package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun WifiArcsIcon(
    level: Int,
    connected: Boolean,
    enabled: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val visibleLevel =
        when {
            !enabled || !connected -> 0
            else -> level.coerceIn(0, 4)
        }

    Canvas(
        modifier =
            modifier
                .size(18.dp)
                .semantics { this.contentDescription = contentDescription },
    ) {
        val strokeWidth = size.minDimension * 0.11f
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        val origin = Offset(size.width / 2f, size.height * 0.86f)
        val radii = listOf(0.26f, 0.46f, 0.66f)

        radii.forEachIndexed { index, radiusFraction ->
            val radius = size.minDimension * radiusFraction
            val lit = visibleLevel > index
            drawArc(
                color = if (lit) activeColor else inactiveColor,
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(origin.x - radius, origin.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = stroke,
            )
        }

        drawCircle(
            color = if (visibleLevel > 0) activeColor else inactiveColor,
            radius = size.minDimension * 0.07f,
            center = origin,
        )

        if (!enabled || !connected) {
            drawLine(
                color = inactiveColor,
                start = Offset(size.width * 0.18f, size.height * 0.18f),
                end = Offset(size.width * 0.82f, size.height * 0.82f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun BatteryLevelIcon(
    percent: Int,
    charging: Boolean,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val fill = percent.coerceIn(0, 100) / 100f

    Canvas(
        modifier =
            modifier
                .size(20.dp, 12.dp)
                .semantics { this.contentDescription = contentDescription },
    ) {
        val strokeWidth = size.minDimension * 0.14f
        val bodyWidth = size.width * 0.82f
        val bodyHeight = size.height * 0.88f
        val bodyTop = (size.height - bodyHeight) / 2f
        val corner = CornerRadius(strokeWidth * 1.05f, strokeWidth * 1.05f)

        drawRoundRect(
            color = color,
            topLeft = Offset(strokeWidth / 2f, bodyTop),
            size = Size(bodyWidth - strokeWidth / 2f, bodyHeight),
            cornerRadius = corner,
            style = Stroke(width = strokeWidth),
        )

        val nubHeight = bodyHeight * 0.42f
        drawRoundRect(
            color = color,
            topLeft = Offset(bodyWidth, (size.height - nubHeight) / 2f),
            size = Size(size.width - bodyWidth, nubHeight),
            cornerRadius = CornerRadius(strokeWidth * 0.55f, strokeWidth * 0.55f),
        )

        val innerLeft = strokeWidth
        val innerTop = bodyTop + strokeWidth / 2f
        val innerWidth = (bodyWidth - strokeWidth * 1.45f).coerceAtLeast(0f)
        val innerHeight = (bodyHeight - strokeWidth).coerceAtLeast(0f)
        val fillWidth = innerWidth * fill
        if (fillWidth > 0f) {
            drawRoundRect(
                color = color,
                topLeft = Offset(innerLeft, innerTop),
                size = Size(fillWidth, innerHeight),
                cornerRadius = CornerRadius(strokeWidth * 0.35f, strokeWidth * 0.35f),
            )
        }

        if (charging) {
            val bolt =
                Path().apply {
                    moveTo(bodyWidth * 0.58f, bodyTop + innerHeight * 0.08f)
                    lineTo(bodyWidth * 0.36f, bodyTop + innerHeight * 0.52f)
                    lineTo(bodyWidth * 0.48f, bodyTop + innerHeight * 0.52f)
                    lineTo(bodyWidth * 0.40f, bodyTop + innerHeight * 0.92f)
                    lineTo(bodyWidth * 0.66f, bodyTop + innerHeight * 0.42f)
                    lineTo(bodyWidth * 0.53f, bodyTop + innerHeight * 0.42f)
                    close()
                }
            drawPath(
                path = bolt,
                color = HomeChromeBackground,
                style =
                    Stroke(
                        width = strokeWidth * 1.15f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
            )
            drawPath(path = bolt, color = Color.White, style = Fill)
        }
    }
}
