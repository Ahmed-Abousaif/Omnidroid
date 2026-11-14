package com.omnidroid.app.mobile.feature.library

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.GameCardInfoStyle
import com.omnidroid.app.mobile.shared.compose.ui.GameCoverOrigin
import com.omnidroid.app.mobile.shared.compose.ui.LibraryGameCardAspectRatio
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.compose.ui.LocalAdaptiveLayout
import com.omnidroid.app.mobile.shared.compose.ui.OmnidroidGameCard
import com.omnidroid.app.mobile.shared.compose.ui.OmnidroidGameImage
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.mobile.shared.compose.ui.gameCoverSharedBounds
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow
import com.omnidroid.app.mobile.shared.controller.reportControllerGame
import com.omnidroid.lib.library.db.entity.Game

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun LibraryGameCardItem(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    game: Game,
    modifier: Modifier = Modifier,
    continueAction: Boolean,
    infoStyle: GameCardInfoStyle,
    fillCard: Boolean,
    decodeWidth: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val cornerAnim by libraryCoverCorner(animatedVisibilityScope, game.id, 4.dp)
    val coverSharedModifier = Modifier.gameCoverSharedBounds(
        sharedTransitionScope, animatedVisibilityScope, game.id, cornerAnim,
        if (continueAction) GameCoverOrigin.Continue else GameCoverOrigin.Library,
    )
    OmnidroidGameCard(
        modifier = modifier,
        coverModifier = coverSharedModifier,
        game = game,
        continueAction = continueAction,
        infoStyle = infoStyle,
        fillCard = fillCard,
        decodeWidth = decodeWidth,
        cornerRadius = cornerAnim,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun LibraryGameRow(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    game: Game,
    modifier: Modifier = Modifier,
    continueAction: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val rowCorner = 10.dp
    val coverInset = 4.dp
    val shape = RoundedCornerShape(rowCorner)
    val cornerAnim by libraryCoverCorner(animatedVisibilityScope, game.id, (rowCorner - coverInset).coerceAtLeast(0.dp))
    val coverSharedModifier = Modifier.gameCoverSharedBounds(
        sharedTransitionScope, animatedVisibilityScope, game.id, cornerAnim,
        if (continueAction) GameCoverOrigin.Continue else GameCoverOrigin.Library,
    )
    val adaptiveSpec = LocalAdaptiveLayout.current
    val thumbHeight = adaptiveSpec.listThumbHeight
    val thumbWidth = thumbHeight * LibraryGameCardAspectRatio
    val availabilityAlpha = if (game.isAvailable) 1f else 0.45f
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .alpha(availabilityAlpha)
                .controllerFocusGlow(shape)
                .reportControllerGame(game)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        shape = shape,
        color = chromeColors.listRow,
        shadowElevation = 4.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, chromeColors.listRowBorder),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(thumbHeight + coverInset * 2)
                    .padding(end = coverInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OmnidroidGameImage(
                modifier =
                    Modifier
                        .padding(start = coverInset)
                        .size(thumbWidth, thumbHeight)
                        .clip(RoundedCornerShape(cornerAnim))
                        .then(coverSharedModifier),
                game = game,
                aspectRatio = null,
                decodeWidth = thumbWidth,
            )
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = game.displayName,
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    color = chromeColors.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LibraryGameSubtext(
                    game = game,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // Trailing markers match the cover's height so the row inset frames them on every side.
            val trailingShape = RoundedCornerShape(rowCorner - coverInset)
            when {
                continueAction -> {
                    Row(
                        modifier =
                            Modifier
                                .height(thumbHeight)
                                .clip(trailingShape)
                                .background(LibraryNeonGreen)
                                .padding(start = 12.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = Color.White,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.continue_playing),
                            style =
                                MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }
                game.isFavorite -> {
                    Box(
                        modifier =
                            Modifier
                                .size(thumbHeight)
                                .clip(trailingShape)
                                .background(LibraryNeonGreen.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = stringResource(R.string.favorites),
                            modifier = Modifier.size(24.dp),
                            tint = LibraryNeonGreen,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun libraryCoverCorner(
    visibility: AnimatedVisibilityScope,
    gameId: Int,
    restingCorner: Dp,
): State<Dp> = visibility.transition.animateDp(
    label = "coverCorner-$gameId",
    transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) },
) { target -> if (target == EnterExitState.Visible) restingCorner else 16.dp }
