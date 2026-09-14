package com.swordfish.lemuroid.app.mobile.feature.gamedetails

import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.HomeChromeBackground
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.swordfish.lemuroid.app.mobile.shared.controller.LocalControllerNavigation
import com.swordfish.lemuroid.app.mobile.shared.controller.controllerFocusGlow
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.app.utils.games.GameUtils
import com.swordfish.lemuroid.lib.library.db.entity.Game
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

val GameDetailsScreenPadding = 20.dp
val GameDetailsColumnGap = 24.dp
const val GameDetailsCoverWeight = 0.42f
const val GameDetailsInfoWeight = 0.58f

@Composable
fun GameDetailsScreen(
    modifier: Modifier = Modifier,
    viewModel: GameDetailsViewModel,
    onBack: () -> Unit,
    onPlay: (Game) -> Unit,
    onFavoriteToggle: (Game, Boolean) -> Unit,
    onOpenSettings: (Game) -> Unit,
    transitionProgress: Float = 1f,
    hideCover: Boolean = false,
    onCoverBounds: (Rect) -> Unit = {},
) {
    val state = viewModel.state.collectAsState().value
    val game = state.game
    if (game == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val controllerNav = LocalControllerNavigation.current
    LaunchedEffect(game.id) {
        controllerNav?.setFocusedGame(game)
    }

    val contentFade = ((transitionProgress - 0.42f) / 0.58f).coerceIn(0f, 1f)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(HomeChromeBackground),
    ) {
        Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = transitionProgress }) {
            GameBackdrop(game)
        }
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(GameDetailsScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(GameDetailsColumnGap),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .weight(GameDetailsCoverWeight),
                contentAlignment = Alignment.Center,
            ) {
                GameCoverOrTrailer(
                    modifier = Modifier.fillMaxHeight(),
                    game = game,
                    playingTrailer = state.playingTrailer,
                    trailerHtml = viewModel.trailerHtml(),
                    trailerSearchUrl = viewModel.trailerSearchUrl(),
                    hideCover = hideCover,
                    onCoverBounds = onCoverBounds,
                    onToggleTrailer = { viewModel.toggleTrailer() },
                )
            }
            GameDetailsInfo(
                modifier =
                    Modifier
                        .weight(GameDetailsInfoWeight)
                        .fillMaxHeight()
                        .padding(top = 20.dp)
                        .graphicsLayer { alpha = contentFade },
                game = game,
                state = state,
                onPlay = { onPlay(game) },
                onFavoriteToggle = { onFavoriteToggle(game, !game.isFavorite) },
                onOpenSettings = { onOpenSettings(game) },
            )
        }
    }
}

private val GameCoverCorner = RoundedCornerShape(16.dp)
private const val DefaultCoverAspect = 2f / 3f

@Composable
private fun GameBackdrop(game: Game) {
    val context = LocalContext.current
    val fallback = remember(game) { CoverUtils.getFallbackDrawable(game) }
    val fallbackPainter = rememberDrawablePainter(drawable = fallback)
    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(18.dp) else Modifier

    AsyncImage(
        model = CoverUtils.coverRequest(context, game),
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

@Composable
private fun GameCoverOrTrailer(
    modifier: Modifier,
    game: Game,
    playingTrailer: Boolean,
    trailerHtml: String?,
    trailerSearchUrl: String?,
    hideCover: Boolean,
    onCoverBounds: (Rect) -> Unit,
    onToggleTrailer: () -> Unit,
) {
    var coverAspect by remember(game.id) { mutableStateOf(DefaultCoverAspect) }
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
                .clip(GameCoverCorner)
                .onGloballyPositioned { onCoverBounds(it.boundsInRoot()) }
                .graphicsLayer { alpha = if (hideCover) 0f else 1f }
        Surface(
            modifier = coverModifier,
            shape = GameCoverCorner,
            tonalElevation = 6.dp,
            color = Color.Black,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (playingTrailer) {
                    GameTrailerView(
                        html = trailerHtml,
                        searchUrl = trailerSearchUrl,
                        modifier = Modifier.fillMaxSize(),
                    )
                    IconButton(
                        onClick = onToggleTrailer,
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
                                .clickable(onClick = onToggleTrailer),
                        contentAlignment = Alignment.Center,
                    ) {
                        LemuroidPoster(
                            game = game,
                            modifier = Modifier.fillMaxSize(),
                            onAspectRatio = { coverAspect = it },
                        )
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
}

@Composable
private fun LemuroidPoster(
    game: Game,
    modifier: Modifier,
    onAspectRatio: (Float) -> Unit,
) {
    val context = LocalContext.current
    val fallback = remember(game) { CoverUtils.getFallbackDrawable(game) }
    val fallbackPainter = rememberDrawablePainter(drawable = fallback)
    AsyncImage(
        model = CoverUtils.coverRequest(context, game),
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
private fun GameTrailerView(
    html: String?,
    searchUrl: String?,
    modifier: Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                if (html != null) {
                    loadDataWithBaseURL(
                        "https://www.youtube.com",
                        html,
                        "text/html",
                        "utf-8",
                        null,
                    )
                } else if (searchUrl != null) {
                    loadUrl(
                        searchUrl,
                        mapOf(
                            "Referer" to "https://www.youtube.com",
                            "Referrer-Policy" to "strict-origin-when-cross-origin",
                        ),
                    )
                }
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
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
            Text(
                text = game.displayName,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
