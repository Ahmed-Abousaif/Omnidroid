package com.omnidroid.app.shared.game

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.compose.ui.OmnidroidGameImage
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.db.entity.Game

@Composable
internal fun GameLoadingScreen(
    game: Game,
    system: GameSystem,
    loadingMessage: String? = null,
) {
    val loadingLabel = stringResource(R.string.game_loading_console, stringResource(system.titleResId))

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .safeDrawingPadding()
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
        ) {
            val compact = maxWidth < 440.dp
            val artworkWidth = (maxWidth * 0.355f).coerceAtMost(200.dp)
            val shape = RoundedCornerShape(18.dp)

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = artworkWidth)
                        .height(IntrinsicSize.Min)
                        .clip(shape)
                        .background(Color(0xFF0D0D0D))
                        .border(1.dp, Color(0xFF89898E), shape),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(artworkWidth)
                            .fillMaxHeight(),
                ) {
                    OmnidroidGameImage(
                        game = game,
                        aspectRatio = null,
                        decodeWidth = artworkWidth,
                        modifier = Modifier.matchParentSize().clearAndSetSemantics {},
                    )
                }

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = if (compact) 20.dp else 36.dp, vertical = 28.dp),
                ) {
                    Text(
                        text = game.displayName,
                        color = Color.White,
                        fontSize = if (compact) 18.sp else 24.sp,
                        lineHeight = if (compact) 23.sp else 29.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = loadingLabel,
                        color = Color.White,
                        fontSize = if (compact) 16.sp else 20.sp,
                        lineHeight = if (compact) 21.sp else 25.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(20.dp))
                    GameLoadingIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .progressSemantics()
                                .semantics {
                                    stateDescription = loadingMessage?.takeIf { it.isNotBlank() } ?: loadingLabel
                                },
                    )
                }
            }
        }
    }
}

@Composable
private fun GameLoadingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "Game loading")
    val split by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.65f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "Loading bar split",
    )

    Canvas(modifier) {
        val capRadius = size.height / 2f
        val midpoint = size.width * split
        // Include the rounded caps in the gap so the two segments stay separated.
        val halfGap = 3.dp.toPx() + capRadius
        drawLine(
            color = LibraryNeonGreen,
            start = Offset(capRadius, capRadius),
            end = Offset(midpoint - halfGap, capRadius),
            strokeWidth = size.height,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = LibraryNeonGreen,
            start = Offset(midpoint + halfGap, capRadius),
            end = Offset(size.width - capRadius, capRadius),
            strokeWidth = size.height,
            cap = StrokeCap.Round,
        )
    }
}
