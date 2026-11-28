package com.omnidroid.app.mobile.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.mobile.shared.compose.ui.chromeContentColor
import com.omnidroid.app.mobile.shared.compose.ui.chromeMutedContentColor
import com.omnidroid.app.mobile.shared.compose.ui.libraryAccentColor
import com.omnidroid.app.shared.ra.RaBadgeImage
import com.omnidroid.lib.library.db.entity.RaGameCache
import com.omnidroid.lib.ra.RaAchievement
import com.omnidroid.lib.ra.RaAchievementBucket
import com.omnidroid.lib.ra.RaUserInfo
import kotlinx.serialization.json.Json
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val PROFILE_RA_JSON = Json { ignoreUnknownKeys = true }

internal const val RA_WARNING_ACHIEVEMENT_ID = 101_000_001

data class ProfileRaGameSummary(
    val gameId: Int,
    val raGameId: Int,
    val title: String,
    val badgeUrl: String,
    val unlockedCount: Int,
    val coreCount: Int,
    val pointsUnlocked: Int,
    val pointsCore: Int,
    val beaten: Boolean,
    val mastered: Boolean,
    val updatedAt: Long,
)

data class ProfileRaUnlockedAchievement(
    val gameId: Int,
    val gameTitle: String,
    val achievement: RaAchievement,
)

data class ProfileRaLibraryAggregate(
    val games: List<ProfileRaGameSummary> = emptyList(),
    val unlockedAchievements: List<ProfileRaUnlockedAchievement> = emptyList(),
    val totalUnlocked: Int = 0,
    val totalCore: Int = 0,
    val totalPointsUnlocked: Int = 0,
    val totalPointsCore: Int = 0,
    val masteredGames: Int = 0,
)

internal fun aggregateProfileRetroAchievements(entries: List<RaGameCache>): ProfileRaLibraryAggregate {
    if (entries.isEmpty()) return ProfileRaLibraryAggregate()

    val games = ArrayList<ProfileRaGameSummary>(entries.size)
    val unlocked = mutableListOf<ProfileRaUnlockedAchievement>()
    var totalUnlocked = 0
    var totalCore = 0
    var totalPointsUnlocked = 0
    var totalPointsCore = 0
    var masteredGames = 0

    for (entry in entries) {
        val displayTitle = entry.title.ifBlank { "RA #${entry.raGameId}" }
        val mastered = entry.completedTime > 0L
        val beaten = entry.beatenTime > 0L
        if (mastered) masteredGames++
        totalUnlocked += entry.numUnlockedAchievements
        totalCore += entry.numCoreAchievements
        totalPointsUnlocked += entry.pointsUnlocked
        totalPointsCore += entry.pointsCore

        games +=
            ProfileRaGameSummary(
                gameId = entry.gameId,
                raGameId = entry.raGameId,
                title = displayTitle,
                badgeUrl = entry.badgeUrl,
                unlockedCount = entry.numUnlockedAchievements,
                coreCount = entry.numCoreAchievements,
                pointsUnlocked = entry.pointsUnlocked,
                pointsCore = entry.pointsCore,
                beaten = beaten,
                mastered = mastered,
                updatedAt = entry.updatedAt,
            )

        val buckets =
            runCatching {
                PROFILE_RA_JSON.decodeFromString<List<RaAchievementBucket>>(entry.achievementsJson)
            }.getOrDefault(emptyList())

        buckets
            .asSequence()
            .flatMap { it.achievements.asSequence() }
            .filter { it.unlocked != 0 && it.id != RA_WARNING_ACHIEVEMENT_ID }
            .distinctBy { it.id }
            .forEach { ach ->
                unlocked +=
                    ProfileRaUnlockedAchievement(
                        gameId = entry.gameId,
                        gameTitle = displayTitle,
                        achievement = ach,
                    )
            }
    }

    unlocked.sortWith(
        compareByDescending<ProfileRaUnlockedAchievement> { it.achievement.unlockTime }
            .thenBy { it.gameTitle }
            .thenBy { it.achievement.title },
    )

    return ProfileRaLibraryAggregate(
        games = games,
        unlockedAchievements = unlocked,
        totalUnlocked = totalUnlocked,
        totalCore = totalCore,
        totalPointsUnlocked = totalPointsUnlocked,
        totalPointsCore = totalPointsCore,
        masteredGames = masteredGames,
    )
}

@Composable
internal fun ProfileRaAccountCard(
    signedIn: Boolean,
    username: String?,
    userInfo: RaUserInfo?,
    loadingUser: Boolean,
    aggregate: ProfileRaLibraryAggregate,
    onOpenSettings: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = chromeColors.panel.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, chromeColors.content.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val activeName = userInfo?.displayName?.ifBlank { userInfo.username } ?: username
            val avatarUrl =
                userInfo?.avatarUrl?.ifBlank { null }
                    ?: activeName?.takeIf { signedIn }?.let { "https://media.retroachievements.org/UserPic/$it.png" }
                    ?: ""
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RaBadgeImage(
                    badgeUrl = avatarUrl,
                    contentDescription = activeName ?: stringResource(R.string.settings_category_retroachievements),
                    size = 48.dp,
                    cornerRadius = 24.dp,
                    fallbackTint = if (signedIn) libraryAccentColor else chromeMutedContentColor,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text =
                            if (signedIn && !activeName.isNullOrBlank()) {
                                activeName
                            } else {
                                stringResource(R.string.profile_ra_not_signed_in_title)
                            },
                        color = chromeContentColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when {
                        userInfo != null -> {
                            val scoreParts =
                                buildList {
                                    add(
                                        stringResource(
                                            R.string.profile_ra_hardcore_pts,
                                            String.format(Locale.getDefault(), "%,d", userInfo.score),
                                        ),
                                    )
                                    if (userInfo.scoreSoftcore > 0) {
                                        add(
                                            stringResource(
                                                R.string.profile_ra_softcore_pts,
                                                String.format(Locale.getDefault(), "%,d", userInfo.scoreSoftcore),
                                            ),
                                        )
                                    }
                                }
                            Text(
                                text = scoreParts.joinToString(" • "),
                                color = libraryAccentColor,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        signedIn && loadingUser -> {
                            Text(
                                text = stringResource(R.string.profile_ra_account_loading),
                                color = chromeMutedContentColor,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        signedIn && !activeName.isNullOrBlank() -> {
                            Text(
                                text = stringResource(R.string.profile_ra_signed_in_offline, activeName),
                                color = chromeMutedContentColor,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        else -> {
                            Text(
                                text = stringResource(R.string.profile_ra_not_signed_in_subtitle),
                                color = chromeMutedContentColor,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (!signedIn && onOpenSettings != null) {
                    OutlinedButton(onClick = onOpenSettings) {
                        Text(
                            text = stringResource(R.string.profile_ra_open_settings),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            if (aggregate.games.isNotEmpty()) {
                HorizontalDivider(color = chromeColors.divider)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RaAccountStat(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.profile_ra_stat_unlocked),
                        value = "${aggregate.totalUnlocked} / ${aggregate.totalCore}",
                    )
                    RaAccountStat(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.profile_ra_stat_points),
                        value = String.format(Locale.getDefault(), "%,d", aggregate.totalPointsUnlocked),
                    )
                    RaAccountStat(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.profile_ra_stat_games),
                        value =
                            if (aggregate.masteredGames > 0) {
                                "${aggregate.games.size} (${
                                    stringResource(R.string.profile_ra_stat_mastered, aggregate.masteredGames)
                                })"
                            } else {
                                "${aggregate.games.size}"
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun RaAccountStat(
    modifier: Modifier,
    label: String,
    value: String,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label,
            color = chromeMutedContentColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = chromeContentColor,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ProfileRaGameRow(
    game: ProfileRaGameSummary,
    onClick: () -> Unit,
) {
    val progress =
        if (game.coreCount > 0) {
            (game.unlockedCount.toFloat() / game.coreCount.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = chromeColors.panel.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, chromeColors.content.copy(alpha = 0.08f)),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.profile_open_game),
                    onClick = onClick,
                ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RaBadgeImage(
                badgeUrl = game.badgeUrl,
                contentDescription = game.title,
                size = 40.dp,
                cornerRadius = 6.dp,
                fallbackTint = chromeMutedContentColor.copy(alpha = 0.75f),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = game.title,
                        color = chromeContentColor,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val badgeStatus =
                        when {
                            game.mastered -> stringResource(R.string.ra_details_mastered)
                            game.beaten -> stringResource(R.string.ra_details_beaten)
                            else -> null
                        }
                    if (badgeStatus != null) {
                        Text(
                            text = badgeStatus,
                            color = libraryAccentColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    text =
                        stringResource(
                            R.string.ra_details_score_summary,
                            game.unlockedCount,
                            game.coreCount,
                            game.pointsUnlocked,
                            game.pointsCore,
                        ),
                    color = chromeMutedContentColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp).height(3.dp),
                    color = libraryAccentColor,
                    trackColor = chromeColors.divider,
                )
            }
        }
    }
}

@Composable
internal fun ProfileRaSectionHeader(title: String) {
    Text(
        text = title,
        color = chromeContentColor,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
internal fun formatRaUnlockContext(
    gameTitle: String,
    unlockTimeSeconds: Long,
): String {
    if (unlockTimeSeconds <= 0L) return gameTitle
    val dateText =
        remember(unlockTimeSeconds) {
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(unlockTimeSeconds * 1000L))
        }
    return "$gameTitle • $dateText"
}
