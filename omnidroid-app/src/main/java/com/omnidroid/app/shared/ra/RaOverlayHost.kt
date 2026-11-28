package com.omnidroid.app.shared.ra

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.CutoutClearance
import com.omnidroid.app.mobile.shared.compose.ui.rememberHuggingCutoutInsets
import com.omnidroid.app.shared.game.BaseGameScreenViewModel
import com.omnidroid.app.utils.android.settings.booleanPreferenceState
import com.omnidroid.app.utils.android.settings.stringPreferenceState
import com.omnidroid.lib.ra.RaAchievement
import com.omnidroid.lib.ra.RaLeaderboardTracker
import com.omnidroid.lib.ra.RaNotificationPosition
import com.omnidroid.lib.ra.RetroAchievementsEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

private data class RaBannerNotification(
    val id: Long,
    val title: String,
    val subtitle: String,
    val badgeUrl: String = "",
    val isMastery: Boolean = false,
    val isError: Boolean = false,
    val isLeaderboard: Boolean = false,
)

/**
 * Non-intercepting in-game RetroAchievements HUD displaying a FIFO toast queue, active
 * challenge badges, measured progress pills, leaderboard trackers, and offline warning state.
 */
@Composable
fun RaOverlayHost(viewModel: BaseGameScreenViewModel) {
    val context = LocalContext.current
    val raEnabled = booleanPreferenceState(R.string.pref_key_ra_enabled, false).value
    val notificationsEnabled = booleanPreferenceState(R.string.pref_key_ra_notifications, true).value
    val positionKey = stringPreferenceState(R.string.pref_key_ra_notification_position, "top_left").value
    val soundsEnabled = booleanPreferenceState(R.string.pref_key_ra_sounds, true).value

    val engine = viewModel.raEngine
    val sessionState by viewModel.hardcorePolicy.sessionState.collectAsState()
    val isDisconnected by engine.isDisconnected.collectAsState()

    if (!raEnabled && !sessionState.moduleLoaded) return

    val bannerQueue = remember { mutableStateListOf<RaBannerNotification>() }
    var currentBanner by remember { mutableStateOf<RaBannerNotification?>(null) }
    val activeChallenges = remember { mutableStateMapOf<Int, RaAchievement>() }
    val activeProgress = remember { mutableStateMapOf<Int, RaAchievement>() }
    val activeTrackers = remember { mutableStateMapOf<Int, RaLeaderboardTracker>() }
    var nextBannerId by remember { mutableStateOf(1L) }

    LaunchedEffect(engine) {
        engine.events.collect { event ->
            when (event) {
                is RetroAchievementsEvent.AchievementTriggered -> {
                    activeChallenges.remove(event.achievement.id)
                    activeProgress.remove(event.achievement.id)
                    if (notificationsEnabled) {
                        bannerQueue.add(
                            RaBannerNotification(
                                id = nextBannerId++,
                                title =
                                    context.getString(
                                        R.string.ra_banner_unlocked,
                                        event.achievement.points,
                                    ),
                                subtitle = event.achievement.title,
                                badgeUrl = event.achievement.badgeUrl,
                            ),
                        )
                    }
                    if (soundsEnabled && notificationsEnabled) {
                        RaSoundPlayer.playUnlockChime()
                    }
                }

                is RetroAchievementsEvent.GameCompleted -> {
                    val isHardcore = viewModel.hardcorePolicy.isHardcoreActive
                    if (notificationsEnabled) {
                        val activeTitle =
                            engine.activeGame
                                .value
                                ?.title
                                .orEmpty()
                        val activeBadge =
                            engine.activeGame
                                .value
                                ?.badgeUrl
                                .orEmpty()
                        bannerQueue.add(
                            RaBannerNotification(
                                id = nextBannerId++,
                                title =
                                    context.getString(
                                        if (isHardcore) R.string.ra_banner_mastered else R.string.ra_banner_completed,
                                    ),
                                subtitle = activeTitle,
                                badgeUrl = activeBadge,
                                isMastery = true,
                            ),
                        )
                    }
                    if (soundsEnabled && notificationsEnabled) {
                        RaSoundPlayer.playMasteryChime()
                    }
                }

                is RetroAchievementsEvent.SubsetCompleted -> {
                    if (notificationsEnabled) {
                        val activeTitle =
                            engine.activeGame
                                .value
                                ?.title
                                .orEmpty()
                        bannerQueue.add(
                            RaBannerNotification(
                                id = nextBannerId++,
                                title = context.getString(R.string.ra_banner_subset_completed, event.subset.title),
                                subtitle = activeTitle,
                                badgeUrl = event.subset.badgeUrl,
                                isMastery = true,
                            ),
                        )
                    }
                }

                is RetroAchievementsEvent.LeaderboardSubmitted -> {
                    if (notificationsEnabled) {
                        bannerQueue.add(
                            RaBannerNotification(
                                id = nextBannerId++,
                                title = event.leaderboard.title,
                                subtitle = event.leaderboard.description,
                                isLeaderboard = true,
                            ),
                        )
                    }
                }

                is RetroAchievementsEvent.LeaderboardScoreboard -> {
                    if (notificationsEnabled) {
                        val activeTitle =
                            engine.activeGame
                                .value
                                ?.title
                                .orEmpty()
                        bannerQueue.add(
                            RaBannerNotification(
                                id = nextBannerId++,
                                title =
                                    context.getString(
                                        R.string.ra_banner_leaderboard_submitted,
                                        event.scoreboard.newRank,
                                        event.scoreboard.submittedScore,
                                    ),
                                subtitle = activeTitle,
                                isLeaderboard = true,
                            ),
                        )
                    }
                }

                is RetroAchievementsEvent.ServerError -> {
                    if (notificationsEnabled) {
                        bannerQueue.add(
                            RaBannerNotification(
                                id = nextBannerId++,
                                title = "RetroAchievements (${event.error.api})",
                                subtitle = event.error.errorMessage,
                                isError = true,
                            ),
                        )
                    }
                }

                is RetroAchievementsEvent.ChallengeIndicatorShow -> {
                    activeChallenges[event.achievement.id] = event.achievement
                }

                is RetroAchievementsEvent.ChallengeIndicatorHide -> {
                    activeChallenges.remove(event.achievement.id)
                }

                is RetroAchievementsEvent.ProgressIndicatorShow,
                is RetroAchievementsEvent.ProgressIndicatorUpdate,
                -> {
                    val ach =
                        when (event) {
                            is RetroAchievementsEvent.ProgressIndicatorShow -> event.achievement
                            is RetroAchievementsEvent.ProgressIndicatorUpdate -> event.achievement
                            else -> null
                        }
                    if (ach != null) {
                        activeProgress[ach.id] = ach
                    }
                }

                is RetroAchievementsEvent.ProgressIndicatorHide -> {
                    activeProgress.remove(event.achievement.id)
                }

                is RetroAchievementsEvent.LeaderboardTrackerShow -> {
                    activeTrackers[event.tracker.id] = event.tracker
                }

                is RetroAchievementsEvent.LeaderboardTrackerUpdate -> {
                    activeTrackers[event.tracker.id] = event.tracker
                }

                is RetroAchievementsEvent.LeaderboardTrackerHide -> {
                    activeTrackers.remove(event.tracker.id)
                }

                else -> Unit
            }
        }
    }

    LaunchedEffect(bannerQueue.size, currentBanner) {
        if (currentBanner == null && bannerQueue.isNotEmpty()) {
            val next = bannerQueue.removeAt(0)
            currentBanner = next
            delay(3600L)
            currentBanner = null
            delay(180L)
        }
    }

    val cutoutInsets =
        rememberHuggingCutoutInsets(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical,
            CutoutClearance.InGame,
        )
    val bannerPos = RaNotificationPosition.fromKey(positionKey)
    val bannerAlignment =
        when (bannerPos) {
            RaNotificationPosition.TOP_LEFT -> Alignment.TopStart
            RaNotificationPosition.TOP_CENTER -> Alignment.TopCenter
            RaNotificationPosition.TOP_RIGHT -> Alignment.TopEnd
            RaNotificationPosition.BOTTOM_LEFT -> Alignment.BottomStart
            RaNotificationPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
        }
    val isTopBanner =
        bannerPos == RaNotificationPosition.TOP_LEFT ||
            bannerPos == RaNotificationPosition.TOP_CENTER ||
            bannerPos == RaNotificationPosition.TOP_RIGHT

    val secondaryCorner =
        if (bannerPos == RaNotificationPosition.BOTTOM_RIGHT) {
            Alignment.BottomStart
        } else {
            Alignment.BottomEnd
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(cutoutInsets)
                .padding(12.dp),
    ) {
        AnimatedVisibility(
            visible = currentBanner != null && notificationsEnabled,
            enter = fadeIn() + slideInVertically { if (isTopBanner) -it / 2 else it / 2 },
            exit = fadeOut() + slideOutVertically { if (isTopBanner) -it / 2 else it / 2 },
            modifier = Modifier.align(bannerAlignment),
        ) {
            currentBanner?.let { banner ->
                RaBannerCard(banner)
            }
        }

        Column(
            modifier = Modifier.align(secondaryCorner),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment =
                if (secondaryCorner == Alignment.BottomEnd) Alignment.End else Alignment.Start,
        ) {
            if (isDisconnected) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xCC5C1B1B),
                    border = BorderStroke(1.dp, Color(0xFFE57373)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudOff,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(R.string.ra_disconnected_warning),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
            }

            if (notificationsEnabled && activeProgress.isNotEmpty()) {
                activeProgress.values.take(2).forEach { ach ->
                    RaProgressPill(ach)
                }
            }

            if (notificationsEnabled && activeTrackers.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    activeTrackers.values.take(3).forEach { tracker ->
                        RaTrackerPill(tracker)
                    }
                }
            }

            if (notificationsEnabled && activeChallenges.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    activeChallenges.values.take(4).forEach { ach ->
                        RaChallengeBadge(ach)
                    }
                }
            }
        }
    }
}

@Composable
private fun RaBannerCard(banner: RaBannerNotification) {
    val accent =
        when {
            banner.isError -> Color(0xFFE57373)
            banner.isMastery -> Color(0xFFFFD54F)
            else -> Color(0xFF81C784)
        }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xDD14171D),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
        shadowElevation = 6.dp,
        modifier = Modifier.widthIn(min = 200.dp, max = 330.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val fallbackIcon =
                when {
                    banner.isLeaderboard -> Icons.Outlined.Leaderboard
                    banner.isMastery -> Icons.Outlined.MilitaryTech
                    else -> Icons.Outlined.EmojiEvents
                }
            RaBadgeImage(
                badgeUrl = banner.badgeUrl,
                contentDescription = banner.subtitle.ifBlank { banner.title },
                size = 40.dp,
                cornerRadius = 8.dp,
                fallbackIcon = fallbackIcon,
                fallbackTint = accent,
                fallbackSize = 28.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = banner.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (banner.subtitle.isNotBlank()) {
                    Text(
                        text = banner.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RaChallengeBadge(ach: RaAchievement) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xCC14171D),
        border = BorderStroke(1.dp, Color(0xFFFFD54F)),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RaBadgeImage(
                badgeUrl = ach.badgeUrl,
                contentDescription = ach.title,
                size = 26.dp,
                cornerRadius = 6.dp,
                fallbackIcon = Icons.Outlined.Verified,
                fallbackTint = Color(0xFFFFD54F),
                fallbackSize = 20.dp,
            )
        }
    }
}

@Composable
private fun RaProgressPill(ach: RaAchievement) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xCC14171D),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (ach.badgeUrl.isNotBlank()) {
                RaBadgeImage(
                    badgeUrl = ach.badgeUrl,
                    contentDescription = ach.title,
                    size = 22.dp,
                    cornerRadius = 4.dp,
                )
            }
            Text(
                text =
                    ach.measuredProgress.ifBlank {
                        "${ach.measuredPercent.toInt()}%"
                    },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun RaTrackerPill(tracker: RaLeaderboardTracker) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xCC14171D),
        border = BorderStroke(1.dp, Color(0xFF64B5F6).copy(alpha = 0.7f)),
    ) {
        Text(
            text = tracker.display,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private object RaSoundPlayer {
    private const val SAMPLE_RATE = 22050

    suspend fun playUnlockChime() =
        playNotes(
            floatArrayOf(587.33f, 880.00f),
            durationMsPerNote = 110,
        )

    suspend fun playMasteryChime() =
        playNotes(
            floatArrayOf(523.25f, 659.25f, 783.99f, 1046.50f),
            durationMsPerNote = 105,
        )

    private suspend fun playNotes(
        freqs: FloatArray,
        durationMsPerNote: Int,
    ) = withContext(Dispatchers.Default) {
        runCatching {
            val samplesPerNote = (SAMPLE_RATE * durationMsPerNote) / 1000
            val pcm = ShortArray(samplesPerNote * freqs.size)
            for (n in freqs.indices) {
                val freq = freqs[n]
                val base = n * samplesPerNote
                for (i in 0 until samplesPerNote) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val env = exp(-4.5f * (i.toFloat() / samplesPerNote))
                    val wave = sin(2.0 * PI * freq * t)
                    pcm[base + i] = (wave * env * 9000.0).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
            val rawMinBuf =
                AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            val minBuf = rawMinBuf.coerceAtLeast(pcm.size * 2)
            val audioAttrs =
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            val audioFmt =
                AudioFormat
                    .Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            val track =
                AudioTrack
                    .Builder()
                    .setAudioAttributes(audioAttrs)
                    .setAudioFormat(audioFmt)
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            delay((freqs.size * durationMsPerNote + 80).toLong())
            runCatching { track.stop() }
            track.release()
        }
    }
}
