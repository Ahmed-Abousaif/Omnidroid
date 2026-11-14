package com.omnidroid.app.mobile.feature.gamedetails

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.omnidroid.R
import com.omnidroid.app.utils.android.benchmarkTag
import com.omnidroid.app.mobile.feature.library.ConsoleBackdrop
import com.omnidroid.app.mobile.feature.library.bleedTop
import com.omnidroid.app.mobile.shared.compose.dialog.DialogWidth
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogButton
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogTextField
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.mobile.shared.compose.ui.GameCoverOrigin
import com.omnidroid.app.mobile.shared.compose.ui.gameCoverSharedBounds
import com.omnidroid.app.mobile.shared.compose.ui.chromeMutedContentColor
import com.omnidroid.app.mobile.shared.compose.ui.homeChromeBackground
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.app.utils.games.GameUtils
import com.omnidroid.lib.library.db.entity.Game
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

val GameDetailsScreenPadding = 20.dp
val GameDetailsColumnGap = 24.dp
const val GameDetailsCoverWeight = 0.42f

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GameDetailsScreen(
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: GameDetailsViewModel,
    onBack: () -> Unit,
    onPlay: (Game) -> Unit,
    onFavoriteToggle: (Game, Boolean) -> Unit,
    onOpenSettings: (Game) -> Unit,
    onSetCustomName: (Game, String) -> Unit = { _, _ -> },
    onSetCustomThumbnail: (Game, Uri) -> Unit = { _, _ -> },
    backdropTopBleed: Dp = 0.dp,
    coverOrigin: GameCoverOrigin = GameCoverOrigin.Library,
) {
    val state = viewModel.state.collectAsState().value
    val game = state.game

    var showNameDialog by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }
    var pendingCoverGame by remember { mutableStateOf<Game?>(null) }
    val coverPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val coverGame = pendingCoverGame
            pendingCoverGame = null
            if (uri != null && coverGame != null) {
                onSetCustomThumbnail(coverGame, uri)
            }
        }

    val controllerNav = LocalControllerNavigation.current
    LaunchedEffect(game?.id) {
        if (game != null) {
            controllerNav?.setFocusedGame(game)
        }
    }

    if (game == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val context = LocalContext.current
    val initialAspect = remember(game.id) {
        CoverUtils.getCachedCoverAspectRatio(context, game, state.metadata.coverImageUrl)
            ?: com.omnidroid.app.mobile.shared.compose.ui.LibraryGameCardAspectRatio
    }
    var naturalAspect by remember(game.id) { mutableStateOf(initialAspect) }
    var selectedTab by rememberSaveable(game.id) { mutableStateOf(GameDetailsTab.Info) }

    val cornerAnim by animatedVisibilityScope.transition.animateDp(
        label = "detailsCoverCorner",
        transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) },
    ) { targetState ->
        if (targetState == EnterExitState.Visible) 16.dp else 4.dp
    }
    val infoAlpha by animatedVisibilityScope.transition.animateFloat(
        label = "infoAlpha",
        transitionSpec = {
            tween(durationMillis = 350, delayMillis = 50, easing = FastOutSlowInEasing)
        },
    ) { targetState ->
        if (targetState == EnterExitState.Visible) 1f else 0f
    }
    val infoOffset by animatedVisibilityScope.transition.animateFloat(
        label = "infoOffset",
        transitionSpec = {
            tween(durationMillis = 350, delayMillis = 50, easing = FastOutSlowInEasing)
        },
    ) { targetState ->
        if (targetState == EnterExitState.Visible) 0f else 28f
    }
    val backdropModifier = remember(backdropTopBleed) {
        Modifier.fillMaxSize().bleedTop(backdropTopBleed)
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .benchmarkTag("game-details-ready")
                .homeChromeBackground(),
    ) {
        ConsoleBackdrop(
            game = game,
            modifier = backdropModifier,
        )
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = GameDetailsScreenPadding,
                        end = GameDetailsScreenPadding,
                        bottom = GameDetailsScreenPadding,
                        top = 10.dp,
                    ),
        ) {
            val portrait = maxHeight > maxWidth
            val coverMaxWidth =
                if (portrait) maxWidth else (maxWidth - GameDetailsColumnGap) * GameDetailsCoverWeight
            val coverMaxHeight =
                if (portrait) {
                    maxHeight * GameDetailsCoverWeight
                } else {
                    (maxHeight - DetailsActionHeight - 16.dp - DetailsActionShadowPadding).coerceAtLeast(1.dp)
                }
            // Size the column to the visible cover, instead of leaving a weighted gutter beside it.
            // Very short windows can scroll this column to keep all three actions reachable.
            val coverWidth =
                minOf(coverMaxWidth, coverMaxHeight * naturalAspect)
                    .coerceAtLeast(minOf(DetailsActionsMinWidth, coverMaxWidth))
            val cover: @Composable (Modifier) -> Unit = { coverModifier ->
                GameCoverOrTrailer(
                    modifier = coverModifier,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    game = game,
                    preferredCoverUrl = state.metadata.coverImageUrl,
                    trailerUrl = state.metadata.trailerUrl,
                    playingTrailer = state.playingTrailer,
                    cornerRadius = cornerAnim,
                    coverOrigin = coverOrigin,
                    aspectRatio = naturalAspect,
                    onAspectRatio = { naturalAspect = it },
                    onToggleTrailer = { viewModel.toggleTrailer() },
                    onStopTrailer = { viewModel.stopTrailer() },
                    onEditCover = {
                        pendingCoverGame = game
                        coverPicker.launch("image/*")
                    },
                )
            }
            val artwork: @Composable (Modifier) -> Unit = { artworkModifier ->
                Column(
                    modifier =
                        artworkModifier
                            .width(coverWidth)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = DetailsActionShadowPadding),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    cover(Modifier.fillMaxWidth().height(coverWidth / naturalAspect))
                    GameDetailsActions(
                        isFavorite = game.isFavorite,
                        onPlay = { onPlay(game) },
                        onFavoriteToggle = { onFavoriteToggle(game, !game.isFavorite) },
                        onOpenSettings = { onOpenSettings(game) },
                    )
                }
            }
            val infoModifier =
                Modifier.graphicsLayer {
                    alpha = infoAlpha
                    if (portrait) {
                        translationY = infoOffset
                    } else {
                        translationX = infoOffset
                    }
                }
            val info: @Composable (Modifier) -> Unit = { detailsModifier ->
                GameDetailsInfo(
                    modifier = detailsModifier.then(infoModifier),
                    game = game,
                    state = state,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    onEditName = {
                        nameDraft = game.customName ?: game.title
                        showNameDialog = true
                    },
                )
            }
            if (portrait) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    artwork(Modifier.align(Alignment.CenterHorizontally))
                    info(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(GameDetailsColumnGap),
                ) {
                    artwork(Modifier.fillMaxHeight())
                    info(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }

    if (showNameDialog) {
        OmnidroidDialog(
            width = DialogWidth.Input,
            onDismissRequest = { showNameDialog = false },
            title = { Text(text = stringResource(R.string.game_context_menu_set_custom_name)) },
            text = {
                OmnidroidDialogTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.game_custom_name_hint)) },
                )
            },
            confirmButton = {
                OmnidroidDialogButton(
                    onClick = {
                        onSetCustomName(game, nameDraft)
                        showNameDialog = false
                    },
                ) {
                    Text(text = stringResource(R.string.ok))
                }
            },
            dismissButton = {
                OmnidroidDialogButton(onClick = { showNameDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun GameCoverOrTrailer(
    modifier: Modifier,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    game: Game,
    preferredCoverUrl: String?,
    trailerUrl: String?,
    playingTrailer: Boolean,
    cornerRadius: Dp,
    coverOrigin: GameCoverOrigin,
    aspectRatio: Float,
    onAspectRatio: (Float) -> Unit,
    onToggleTrailer: () -> Unit,
    onStopTrailer: () -> Unit,
    onEditCover: () -> Unit,
) {
    val hasTrailer = !trailerUrl.isNullOrBlank()
    val coverShape = RoundedCornerShape(cornerRadius)
    val coverSharedModifier = Modifier.gameCoverSharedBounds(
        sharedTransitionScope, animatedVisibilityScope, game.id, cornerRadius, coverOrigin,
    )
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val coverModifier =
            Modifier
                .fillMaxHeight()
                .then(
                    if (maxHeight * aspectRatio <= maxWidth) {
                        Modifier.aspectRatio(aspectRatio, matchHeightConstraintsFirst = true)
                    } else {
                        Modifier.fillMaxWidth().aspectRatio(aspectRatio)
                    },
                )
        Box(modifier = coverModifier) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(coverSharedModifier),
                shape = coverShape,
                tonalElevation = 6.dp,
                color = Color(0xFF161616),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (playingTrailer && hasTrailer) {
                        GameTrailerPlayer(
                            trailerUrl = trailerUrl!!,
                            modifier = Modifier.fillMaxSize(),
                        )
                        IconButton(
                            onClick = onStopTrailer,
                            modifier =
                                Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.stop),
                                tint = Color.White,
                            )
                        }
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (hasTrailer) {
                                            Modifier.clickable(onClick = onToggleTrailer)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            OmnidroidPoster(
                                game = game,
                                preferredCoverUrl = preferredCoverUrl,
                                modifier = Modifier.fillMaxSize(),
                                onAspectRatio = onAspectRatio,
                            )
                            if (hasTrailer) {
                                Icon(
                                    imageVector = Icons.Outlined.PlayCircle,
                                    contentDescription = stringResource(R.string.game_trailer),
                                    modifier = Modifier.size(64.dp),
                                    tint = Color.White.copy(alpha = 0.85f),
                                )
                            }
                        }
                    }
                }
            }
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.game_context_menu_change_thumbnail),
                tint = Color.White.copy(alpha = 0.55f),
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onEditCover),
            )
        }
    }
}

@Composable
private fun OmnidroidPoster(
    game: Game,
    preferredCoverUrl: String?,
    modifier: Modifier,
    onAspectRatio: (Float) -> Unit,
) {
    val context = LocalContext.current
    val fallback = remember(game) { CoverUtils.getFallbackDrawable(game) }
    val fallbackPainter = rememberDrawablePainter(drawable = fallback)
    val request = remember(context, game.id, game.coverFrontUrl, game.customCoverPath, preferredCoverUrl) {
        val cover = CoverUtils.coverRequest(context, game, preferredCoverUrl)
        cover.newBuilder().placeholderMemoryCacheKey(cover.memoryCacheKey).build()
    }
    AsyncImage(
        model = request,
        contentDescription = game.displayName,
        modifier = modifier,
        fallback = fallbackPainter,
        error = fallbackPainter,
        contentScale = ContentScale.Crop,
        onSuccess = { state ->
            val size = state.painter.intrinsicSize
            if (size.width > 0f && size.height > 0f) {
                onAspectRatio(size.width / size.height)
            }
        },
        onError = {
            val size = fallbackPainter.intrinsicSize
            if (size.width > 0f && size.height > 0f) {
                onAspectRatio(size.width / size.height)
            }
        },
    )
}

@Composable
private fun GameTrailerPlayer(
    trailerUrl: String,
    modifier: Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            android.widget.VideoView(context).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                setVideoURI(Uri.parse(trailerUrl))
                setOnPreparedListener { player ->
                    player.isLooping = true
                    start()
                }
            }
        },
        update = { videoView ->
            val current = videoView.tag as? String
            if (current != trailerUrl) {
                videoView.tag = trailerUrl
                videoView.setVideoURI(Uri.parse(trailerUrl))
                videoView.start()
            }
        },
        onRelease = { videoView ->
            videoView.stopPlayback()
        },
    )
}

private val DetailsActionCornerRadius = 8.dp
private val DetailsActionCorner = RoundedCornerShape(DetailsActionCornerRadius)
private val DetailsTabPadding = 3.dp
private val DetailsActionHeight = 40.dp
private val DetailsActionShadowPadding = 12.dp
private val DetailsActionsMinWidth = DetailsActionHeight * 3 + 20.dp

private enum class GameDetailsTab {
    Info,
    Achievements,
}

@Composable
private fun GameDetailsInfo(
    modifier: Modifier,
    game: Game,
    state: GameDetailsViewModel.UiState,
    selectedTab: GameDetailsTab,
    onTabSelected: (GameDetailsTab) -> Unit,
    onEditName: () -> Unit,
) {
    val context = LocalContext.current
    val systemName = remember(game.id) { GameUtils.getGameSubtitle(context, game) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onEditName)
                    .padding(end = 4.dp),
        ) {
            Text(
                text = game.displayName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.game_context_menu_change_name),
                tint = chromeMutedContentColor.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp),
            )
        }
        GameDetailsTabs(selectedTab = selectedTab, onTabSelected = onTabSelected)
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            if (selectedTab == GameDetailsTab.Achievements) {
                Text(
                    text = stringResource(R.string.game_retro_achievements_wip),
                    style = MaterialTheme.typography.bodyMedium,
                    color = chromeMutedContentColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
            } else {
                GameDetailsInfoContent(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    game = game,
                    state = state,
                    systemName = systemName,
                )
            }
        }
    }
}

@Composable
private fun GameDetailsTabs(
    selectedTab: GameDetailsTab,
    onTabSelected: (GameDetailsTab) -> Unit,
) {
    val shape = DetailsActionCorner
    val tabShape = RoundedCornerShape(DetailsActionCornerRadius - DetailsTabPadding)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(chromeColors.panel.copy(alpha = 0.16f))
                .border(1.dp, chromeColors.content.copy(alpha = 0.08f), shape)
                .horizontalScroll(rememberScrollState())
                .selectableGroup()
                .padding(DetailsTabPadding),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GameDetailsTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            val label =
                stringResource(if (tab == GameDetailsTab.Info) R.string.game_info else R.string.game_achievements)
            Row(
                modifier =
                    Modifier
                        .height(36.dp)
                        .controllerFocusGlow(tabShape)
                        .clip(tabShape)
                        .background(if (selected) chromeColors.panelSelected else Color.Transparent)
                        .border(
                            1.dp,
                            if (selected) chromeColors.content.copy(alpha = 0.12f) else Color.Transparent,
                            tabShape,
                        )
                        .selectable(selected = selected, role = Role.Tab, onClick = { onTabSelected(tab) })
                        .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector =
                        if (tab == GameDetailsTab.Info) Icons.Outlined.Info else Icons.Outlined.EmojiEvents,
                    contentDescription = null,
                    tint = if (selected) LibraryNeonGreen else chromeMutedContentColor,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) chromeColors.content else chromeMutedContentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun GameDetailsInfoContent(
    modifier: Modifier,
    game: Game,
    state: GameDetailsViewModel.UiState,
    systemName: String,
) {
    Column(modifier = modifier) {
        Text(
            text = systemName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GameStat(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.game_play_time),
                value = formatPlayTime(state.playTimeMs),
            )
            GameStat(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.game_last_played),
                value = formatLastPlayed(game.lastPlayedAt),
            )
        }
        GameStat(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
            label = stringResource(R.string.game_sessions),
            value = "${state.sessionCount}",
        )
    }
}

@Composable
private fun GameDetailsActions(
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val playButton: @Composable (Modifier) -> Unit = { modifier ->
        Surface(
            onClick = onPlay,
            modifier =
                modifier
                    .height(DetailsActionHeight)
                    .controllerFocusGlow(DetailsActionCorner),
            shape = DetailsActionCorner,
            color = LibraryNeonGreen,
            shadowElevation = 6.dp,
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val showLabel = maxWidth >= 76.dp
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = if (showLabel) null else stringResource(R.string.game_play),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    if (showLabel) {
                        Text(
                            text = stringResource(R.string.game_play),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
    val secondaryButtons: @Composable () -> Unit = {
        Surface(
            onClick = onFavoriteToggle,
            modifier =
                Modifier
                    .size(DetailsActionHeight)
                    .controllerFocusGlow(DetailsActionCorner),
            shape = DetailsActionCorner,
            color = chromeColors.iconButton,
            border = BorderStroke(1.dp, chromeColors.iconButtonBorder),
            shadowElevation = 6.dp,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(R.string.favorites),
                    tint = if (isFavorite) LibraryNeonGreen else chromeColors.muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Surface(
            onClick = onOpenSettings,
            modifier =
                Modifier
                    .size(DetailsActionHeight)
                    .controllerFocusGlow(DetailsActionCorner),
            shape = DetailsActionCorner,
            color = chromeColors.iconButton,
            border = BorderStroke(1.dp, chromeColors.iconButtonBorder),
            shadowElevation = 6.dp,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.game_settings),
                    tint = chromeColors.muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        playButton(Modifier.weight(1f))
        secondaryButtons()
    }
}

@Composable
private fun GameStat(
    modifier: Modifier,
    label: String,
    value: String,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun formatPlayTime(playTimeMs: Long): String {
    if (playTimeMs <= 0L) return stringResource(R.string.game_never_played)
    val hours = TimeUnit.MILLISECONDS.toHours(playTimeMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(playTimeMs) % 60
    return when {
        hours > 0 -> stringResource(R.string.game_play_time_hours, hours, minutes)
        minutes > 0 -> stringResource(R.string.game_play_time_minutes, minutes)
        else -> stringResource(R.string.game_play_time_minutes, 1)
    }
}

@Composable
private fun formatLastPlayed(lastPlayedAt: Long?): String {
    if (lastPlayedAt == null) return stringResource(R.string.game_never_played)
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(lastPlayedAt))
}
