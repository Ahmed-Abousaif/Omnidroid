package com.omnidroid.app.shared.ra

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.mobile.shared.compose.ui.chromeMutedContentColor
import com.omnidroid.app.mobile.shared.compose.ui.libraryAccentColor
import com.omnidroid.lib.ra.RaAchievement
import com.omnidroid.lib.ra.RaLeaderboard
import java.util.Locale

@Composable
fun RaGameSummaryHeader(
    badgeUrl: String,
    title: String,
    scoreSummary: String?,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    richPresence: String = "",
    badgeSize: Dp = 48.dp,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RaBadgeImage(
                badgeUrl = badgeUrl,
                contentDescription = title,
                size = badgeSize,
                fallbackTint = chromeMutedContentColor.copy(alpha = 0.75f),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                if (!scoreSummary.isNullOrBlank()) {
                    Text(
                        text = scoreSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = chromeMutedContentColor,
                    )
                }
                if (!statusText.isNullOrBlank()) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = libraryAccentColor,
                    )
                }
            }
        }
        if (richPresence.isNotBlank()) {
            Text(
                text = richPresence,
                style = MaterialTheme.typography.bodySmall,
                color = chromeMutedContentColor,
            )
        }
    }
}

@Composable
fun RaAchievementCard(
    achievement: RaAchievement,
    modifier: Modifier = Modifier,
    useHardcoreRarity: Boolean = false,
    showRarity: Boolean = true,
    dimLockedBadge: Boolean = false,
    containerColor: Color = chromeColors.panel.copy(alpha = 0.25f),
    contextLabel: String? = null,
) {
    val unlocked = achievement.unlocked != 0
    val badge = if (unlocked) achievement.badgeUrl else achievement.badgeLockedUrl.ifBlank { achievement.badgeUrl }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = BorderStroke(1.dp, chromeColors.content.copy(alpha = 0.08f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RaBadgeImage(
                badgeUrl = badge,
                contentDescription = achievement.title,
                size = 40.dp,
                cornerRadius = 6.dp,
                fallbackTint = chromeMutedContentColor.copy(alpha = 0.75f),
                modifier = Modifier.alpha(if (dimLockedBadge && !unlocked) 0.65f else 1f),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = achievement.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text =
                            if (unlocked) {
                                "${achievement.points} pts • ${stringResource(R.string.ra_unlocked_label)}"
                            } else {
                                "${achievement.points} pts"
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unlocked) libraryAccentColor else chromeMutedContentColor,
                    )
                }
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = chromeMutedContentColor,
                )
                if (!contextLabel.isNullOrBlank()) {
                    Text(
                        text = contextLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = chromeMutedContentColor.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showRarity) {
                    Text(
                        text =
                            String.format(
                                Locale.US,
                                stringResource(R.string.ra_rarity_label),
                                if (useHardcoreRarity) achievement.rarityHardcore else achievement.rarity,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = chromeMutedContentColor.copy(alpha = 0.75f),
                    )
                }
                if (!unlocked && achievement.measuredPercent > 0f) {
                    LinearProgressIndicator(
                        progress = { (achievement.measuredPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun RaLeaderboardCard(
    leaderboard: RaLeaderboard,
    modifier: Modifier = Modifier,
    showTracker: Boolean = false,
    containerColor: Color = chromeColors.panel.copy(alpha = 0.2f),
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = BorderStroke(1.dp, chromeColors.content.copy(alpha = 0.08f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = leaderboard.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f),
                )
                if (showTracker && leaderboard.trackerValue.isNotBlank()) {
                    Text(
                        text = leaderboard.trackerValue,
                        style = MaterialTheme.typography.labelSmall,
                        color = libraryAccentColor,
                    )
                }
            }
            Text(
                text = leaderboard.description,
                style = MaterialTheme.typography.bodySmall,
                color = chromeMutedContentColor,
            )
        }
    }
}
