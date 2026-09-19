package com.omnidroid.app.mobile.feature.gamedetails

import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.library.LibraryTopBarRowHeight
import com.omnidroid.app.mobile.shared.compose.ui.HomeChromeBackground
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
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
const val GameDetailsInfoWeight = 0.58f

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

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(HomeChromeBackground),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GameBackdrop(game, state.metadata.backgroundImageUrl)
        }
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = GameDetailsScreenPadding,
                        end = GameDetailsScreenPadding,
                        bottom = GameDetailsScreenPadding,
                    ),
            horizontalArrangement = Arrangement.spacedBy(GameDetailsColumnGap),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .weight(GameDetailsCoverWeight)
                        .padding(top = GameDetailsScreenPadding),
                contentAlignment = Alignment.Center,
            ) {
                GameCoverOrTrailer(
                    modifier = Modifier.fillMaxHeight(),
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    game = game,
                    preferredCoverUrl = state.metadata.coverImageUrl,
                    trailerUrl = state.metadata.trailerUrl,
                    playingTrailer = state.playingTrailer,
                    onToggleTrailer = { viewModel.toggleTrailer() },
                    onStopTrailer = { viewModel.stopTrailer() },
                    onEditCover = {
                        pendingCoverGame = game
                        coverPicker.launch("image/*")
                    },
                )
            }
            GameDetailsInfo(
                modifier =
                    Modifier
                        .weight(GameDetailsInfoWeight)
                        .fillMaxHeight()
                        .padding(top = LibraryTopBarRowHeight),
                game = game,
                state = state,
                onPlay = { onPlay(game) },
                onFavoriteToggle = { onFavoriteToggle(game, !game.isFavorite) },
                onOpenSettings = { onOpenSettings(game) },
                onEditName = {
                    nameDraft = game.customName ?: game.title
                    showNameDialog = true
                },
            )
        }
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(text = stringResource(R.string.game_context_menu_set_custom_name)) },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.game_custom_name_hint)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetCustomName(game, nameDraft)
                        showNameDialog = false
                    },
                ) {
                    Text(text = stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}

private val GameCoverCorner = RoundedCornerShape(16.dp)
private const val DefaultCoverAspect = 2f / 3f

@Composable
private fun GameBackdrop(
    game: Game,
    preferredImageUrl: String?,
) {
    val context = LocalContext.current
    val fallback = remember(game) { CoverUtils.getFallbackDrawable(game) }
    val fallbackPainter = rememberDrawablePainter(drawable = fallback)
    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(18.dp) else Modifier

    AsyncImage(
        model = CoverUtils.coverRequest(context, game, preferredImageUrl),
        contentDescription = null,
        modifier = Modifier.fillMaxSize().then(blurModifier),
        fallback = fallbackPainter,
        error = fallbackPainter,
        contentScale = ContentScale.Crop,
        alpha = 0.22f,
    )
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            HomeChromeBackground.copy(alpha = 0.72f),
                            HomeChromeBackground.copy(alpha = 0.92f),
                        ),
                    ),
                ),
    )
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
    onToggleTrailer: () -> Unit,
    onStopTrailer: () -> Unit,
    onEditCover: () -> Unit,
) {
    val hasTrailer = !trailerUrl.isNullOrBlank()
    var coverAspect by remember(game.id) { mutableStateOf(DefaultCoverAspect) }
    val coverSharedModifier =
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "game-cover-${game.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ ->
                    tween(durationMillis = 480, easing = FastOutSlowInEasing)
                },
                clipInOverlayDuringTransition = OverlayClip(GameCoverCorner),
            )
        }
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val coverModifier =
            Modifier
                .fillMaxHeight()
                .then(
                    if (maxHeight * coverAspect <= maxWidth) {
                        Modifier.aspectRatio(coverAspect, matchHeightConstraintsFirst = true)
                    } else {
                        Modifier.fillMaxWidth().aspectRatio(coverAspect)
                    },
                )
        Box(modifier = coverModifier) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(GameCoverCorner)
                        .then(coverSharedModifier),
                shape = GameCoverCorner,
                tonalElevation = 6.dp,
                color = Color.Black,
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
                                onAspectRatio = { coverAspect = it },
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
    AsyncImage(
        model = CoverUtils.coverRequest(context, game, preferredCoverUrl),
        contentDescription = game.displayName,
        modifier = modifier,
        fallback = fallbackPainter,
        error = fallbackPainter,
        contentScale = ContentScale.Fit,
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

private val DetailsActionCorner = RoundedCornerShape(4.dp)
private val DetailsActionHeight = 52.dp
private val DetailsActionFill = Color(0xFF161616)

@Composable
private fun GameDetailsInfo(
    modifier: Modifier,
    game: Game,
    state: GameDetailsViewModel.UiState,
    onPlay: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditName: () -> Unit,
) {
    val context = LocalContext.current
    val systemName = remember(game.id) { GameUtils.getGameSubtitle(context, game) }

    Column(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
        ) {
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
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.game_context_menu_change_name),
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = systemName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            if (state.loadingMetadata) {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .padding(top = 16.dp)
                            .size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = state.metadata.description ?: stringResource(R.string.game_no_description),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GameStat(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.game_genre),
                    value = state.metadata.genre ?: stringResource(R.string.none),
                )
                GameStat(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.game_release_date),
                    value = state.metadata.releaseDate ?: stringResource(R.string.none),
                )
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GameStat(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.game_publisher),
                    value = state.metadata.publisher ?: stringResource(R.string.none),
                )
                GameStat(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.game_rating),
                    value = state.metadata.rating ?: stringResource(R.string.none),
                )
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 16.dp),
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
        }

        GameDetailsActions(
            isFavorite = game.isFavorite,
            onPlay = onPlay,
            onFavoriteToggle = onFavoriteToggle,
            onOpenSettings = onOpenSettings,
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            onClick = onPlay,
            modifier =
                Modifier
                    .weight(1f)
                    .height(DetailsActionHeight)
                    .controllerFocusGlow(DetailsActionCorner),
            shape = DetailsActionCorner,
            color = LibraryNeonGreen,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
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
        Surface(
            onClick = onFavoriteToggle,
            modifier =
                Modifier
                    .size(DetailsActionHeight)
                    .controllerFocusGlow(DetailsActionCorner),
            shape = DetailsActionCorner,
            color = DetailsActionFill,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(R.string.favorites),
                    tint = if (isFavorite) LibraryNeonGreen else Color.White,
                    modifier = Modifier.size(26.dp),
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
            color = DetailsActionFill,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.game_settings),
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
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
