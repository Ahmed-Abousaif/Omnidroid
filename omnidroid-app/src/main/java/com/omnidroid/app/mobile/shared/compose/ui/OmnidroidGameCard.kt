package com.omnidroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow
import com.omnidroid.app.mobile.shared.controller.reportControllerGame
import com.omnidroid.app.utils.games.GameUtils
import com.omnidroid.lib.library.db.entity.Game

const val LibraryGameCardAspectRatio = 2f / 3f

enum class GameCardInfoStyle {
    BELOW,
    OVERLAY,
    MINIMAL,
}

private val CardCorner = RoundedCornerShape(4.dp)
private val ContinueButtonCorner = RoundedCornerShape(4.dp)
private val CaptionFooterHeight = 52.dp

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun OmnidroidGameCard(
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
    game: Game,
    badge: String? = null,
    continueAction: Boolean = false,
    showTitles: Boolean = true,
    infoStyle: GameCardInfoStyle = GameCardInfoStyle.OVERLAY,
    fillCard: Boolean = false,
    cornerRadius: androidx.compose.ui.unit.Dp = 4.dp,
    onClick: () -> Unit = { },
    onLongClick: () -> Unit = { },
) {
    val cardShape = RoundedCornerShape(cornerRadius)
    if (fillCard && infoStyle == GameCardInfoStyle.BELOW) {
        CaptionBelowGameCard(
            modifier = modifier,
            coverModifier = coverModifier,
            game = game,
            continueAction = continueAction,
            cornerRadius = cornerRadius,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        return
    }

    Surface(
        modifier =
            modifier
                .controllerFocusGlow(cardShape)
                .reportControllerGame(game)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        shape = cardShape,
        color = Color(0xFF161616),
        shadowElevation = 4.dp,
        tonalElevation = 0.dp,
    ) {
        Box(modifier = if (fillCard) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
            OmnidroidGameImage(
                modifier =
                    (if (fillCard) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                        .then(coverModifier),
                game = game,
                aspectRatio = if (fillCard) null else LibraryGameCardAspectRatio,
            )
            CoverForeground(
                game = game,
                badge = badge,
                continueAction = continueAction,
                infoStyle = infoStyle,
                showTitles = showTitles,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CaptionBelowGameCard(
    modifier: Modifier,
    coverModifier: Modifier = Modifier,
    game: Game,
    continueAction: Boolean,
    cornerRadius: androidx.compose.ui.unit.Dp = 4.dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val cardShape = RoundedCornerShape(cornerRadius)
    BoxWithConstraints(modifier = modifier) {
        val coverHeight = (maxHeight - CaptionFooterHeight).coerceAtLeast(48.dp)
        val coverWidth = coverHeight * LibraryGameCardAspectRatio
        Surface(
            modifier =
                Modifier
                    .width(coverWidth)
                    .fillMaxHeight()
                    .controllerFocusGlow(cardShape)
                    .reportControllerGame(game)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    ),
            shape = cardShape,
            color = Color(0xFF161616),
            shadowElevation = 4.dp,
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                OmnidroidGameImage(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(coverModifier),
                    game = game,
                    aspectRatio = null,
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (continueAction) {
                        ContinueButton()
                    } else {
                        GameCardInfo(game = game, overlay = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.CoverForeground(
    game: Game,
    badge: String?,
    continueAction: Boolean,
    infoStyle: GameCardInfoStyle,
    showTitles: Boolean,
) {
    val showOverlayInfo = infoStyle == GameCardInfoStyle.OVERLAY && showTitles && !continueAction
    val showOverlayContinue = infoStyle == GameCardInfoStyle.OVERLAY && continueAction && showTitles
    val showMinimalContinue = infoStyle == GameCardInfoStyle.MINIMAL && continueAction
    if (showOverlayInfo || showOverlayContinue || showMinimalContinue) {
        val scrimAlpha = if (showMinimalContinue) 0.72f else 0.88f
        val padding = if (showMinimalContinue || showOverlayContinue) 6.dp else 10.dp
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = scrimAlpha),
                        ),
                    )
                    .padding(horizontal = padding, vertical = padding),
        ) {
            when {
                showOverlayContinue -> ContinueButton()
                showOverlayInfo -> GameCardInfo(game = game, overlay = true)
                showMinimalContinue -> {
                    Text(
                        text = stringResource(R.string.continue_playing),
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                shadow =
                                    Shadow(
                                        color = Color.Black.copy(alpha = 0.85f),
                                        blurRadius = 6f,
                                    ),
                            ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    if (badge != null && !continueAction) {
        Surface(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary,
        ) {
            Text(
                text = badge,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun ContinueButton() {
    val label = stringResource(R.string.continue_playing)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val baseStyle =
            MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            )
        val fit =
            remember(maxWidth, label, baseStyle, density) {
                val candidates =
                    listOf(
                        Triple(14.sp, 8.dp, 7.dp),
                        Triple(13.sp, 6.dp, 6.dp),
                        Triple(12.sp, 5.dp, 5.dp),
                        Triple(11.sp, 4.dp, 4.dp),
                        Triple(10.sp, 4.dp, 4.dp),
                    )
                candidates.firstOrNull { (fontSize, horizontal, _) ->
                    val style = baseStyle.copy(fontSize = fontSize, lineHeight = fontSize)
                    val textWidth =
                        measurer.measure(
                            text = label,
                            style = style,
                            maxLines = 1,
                            softWrap = false,
                        ).size.width
                    with(density) { textWidth.toDp() } <= maxWidth - horizontal * 2
                } ?: candidates.last()
            }
        val textStyle = baseStyle.copy(fontSize = fit.first, lineHeight = fit.first)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ContinueButtonCorner,
            color = LibraryNeonGreen,
        ) {
            Text(
                text = label,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = fit.second, vertical = fit.third),
                style = textStyle,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun GameCardInfo(
    game: Game,
    overlay: Boolean,
) {
    val context = LocalContext.current
    val consoleName =
        remember(game.systemId) {
            GameUtils.getSystemShortName(context, game)
        }
    val textShadow =
        if (overlay) {
            Shadow(
                color = Color.Black.copy(alpha = 0.85f),
                blurRadius = 8f,
            )
        } else {
            Shadow(color = Color.Transparent)
        }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = consoleName,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    shadow = textShadow,
                ),
            color = Color(0xFFBDBDBD),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = game.displayName,
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    shadow = textShadow,
                ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
