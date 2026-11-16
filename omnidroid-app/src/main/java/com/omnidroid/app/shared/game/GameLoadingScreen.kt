package com.omnidroid.app.shared.game

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
        ) {
            val compact = maxWidth < 320.dp
            val artworkWidth = (maxWidth * 0.32f).coerceAtMost(112.dp)
            val shape = RoundedCornerShape(12.dp)

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
                            .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = game.displayName,
                        color = Color.White,
                        fontSize = if (compact) 14.sp else 16.sp,
                        lineHeight = if (compact) 18.sp else 20.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = loadingLabel,
                        color = Color.White,
                        fontSize = if (compact) 12.sp else 13.sp,
                        lineHeight = if (compact) 16.sp else 18.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(12.dp))
                    GameLoadingIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
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
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "Loading bar fill",
    )

    Canvas(modifier) {
        val cornerRadius = CornerRadius(size.height / 2f)
        drawRoundRect(
            color = LibraryNeonGreen.copy(alpha = 0.18f),
            cornerRadius = cornerRadius,
        )
        drawRoundRect(
            color = LibraryNeonGreen,
            size = Size(size.width * progress, size.height),
            cornerRadius = cornerRadius,
        )
    }
}
