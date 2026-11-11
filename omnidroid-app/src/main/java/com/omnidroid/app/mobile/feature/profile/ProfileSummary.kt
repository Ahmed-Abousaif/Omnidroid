package com.omnidroid.app.mobile.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.mobile.shared.compose.ui.chromeContentColor
import com.omnidroid.app.mobile.shared.compose.ui.chromeMutedContentColor
import java.util.Locale

private val StreakFire = Color(0xFFFF6D00)

@Composable
internal fun ProfileOverview(
    state: ProfileViewModel.UiState,
    onEditTag: () -> Unit,
    onEditPhoto: () -> Unit,
) {
    Column(Modifier.padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ProfileIdentity(state, onEditTag, onEditPhoto)
        ProfileProgress(state)
        ProfilePlaytime(state.totalPlayTimeMs)
    }
    ProfileDivider()
}

@Composable
internal fun ProfileIdentity(
    state: ProfileViewModel.UiState,
    onEditTag: () -> Unit,
    onEditPhoto: () -> Unit,
    compact: Boolean = false,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(if (compact) 48.dp else 64.dp).clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.profile_change_picture),
                onClick = onEditPhoto,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).background(chromeColors.listRow),
                contentAlignment = Alignment.Center,
            ) {
                if (state.profilePicUri != null) {
                    AsyncImage(
                        model = state.profilePicUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        error = painterResource(R.drawable.ic_profile_robot),
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.ic_profile_robot),
                        contentDescription = null,
                        tint = LibraryNeonGreen,
                        modifier = Modifier.size(if (compact) 28.dp else 36.dp),
                    )
                }
            }
            Icon(
                Icons.Outlined.PhotoCamera,
                contentDescription = null,
                tint = chromeContentColor,
                modifier =
                    Modifier.align(Alignment.BottomEnd).background(chromeColors.menu, CircleShape)
                        .padding(3.dp).size(14.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                state.tag,
                color = chromeContentColor,
                fontSize = if (compact) 18.sp else 24.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            state.milestoneBadge?.let { badge ->
                val label =
                    when (badge) {
                        ConsoleAchievementsStore.MilestoneBadge.COLLECTOR -> R.string.profile_badge_collector
                        ConsoleAchievementsStore.MilestoneBadge.ENTHUSIAST -> R.string.profile_badge_enthusiast
                        ConsoleAchievementsStore.MilestoneBadge.HISTORIAN -> R.string.profile_badge_historian
                        ConsoleAchievementsStore.MilestoneBadge.OMNIDROID_MASTER -> R.string.profile_badge_master
                    }
                Row(
                    Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Outlined.EmojiEvents,
                        contentDescription = null,
                        tint = chromeMutedContentColor,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        stringResource(label),
                        color = chromeMutedContentColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        IconButton(onClick = onEditTag) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = stringResource(R.string.profile_change_tag),
                tint = chromeMutedContentColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun ProfileProgress(state: ProfileViewModel.UiState) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.profile_level, state.level),
                color = chromeContentColor,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(
                    R.string.profile_xp_progress,
                    String.format(Locale.getDefault(), "%,d", state.xpCurrent),
                    String.format(Locale.getDefault(), "%,d", state.xpForNext),
                ),
                color = chromeMutedContentColor,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LinearProgressIndicator(
            progress = { state.xpProgress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = LibraryNeonGreen,
            trackColor = chromeColors.divider,
        )
        Text(
            stringResource(R.string.profile_xp_to_next, state.xpToNext),
            color = chromeMutedContentColor,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun ProfilePlaytime(durationMs: Long) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.profile_total_playtime),
            color = chromeMutedContentColor,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(profileDuration(durationMs), color = chromeContentColor, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
internal fun ProfileStreak(
    state: ProfileViewModel.UiState,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Outlined.LocalFireDepartment,
                contentDescription = null,
                tint = StreakFire,
                modifier = Modifier.size(22.dp),
            )
            Text(
                stringResource(R.string.profile_streak_current),
                color = chromeContentColor,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.profile_streak_multiplier, state.streakMultiplier),
                color = if (state.streakMultiplier > 1.0) StreakFire else chromeMutedContentColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    pluralStringResource(R.plurals.profile_days, state.currentStreak, state.currentStreak),
                    color = chromeContentColor,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.profile_best_streak_days, state.bestStreak),
                    color = chromeMutedContentColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                for (day in 1..7) {
                    Box(
                        Modifier.size(if (day == 7) 10.dp else 7.dp).clip(CircleShape).background(
                            if (day <= state.currentStreak) {
                                if (day == 7) LibraryNeonGreen else StreakFire
                            } else {
                                chromeColors.divider
                            },
                        ),
                    )
                }
            }
        }
    }
}
