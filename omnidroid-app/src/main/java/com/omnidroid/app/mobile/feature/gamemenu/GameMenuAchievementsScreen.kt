package com.omnidroid.app.mobile.feature.gamemenu

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.dialog.DialogWidth
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogButton
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuAction
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuSection
import com.omnidroid.app.mobile.shared.compose.sidemenu.SideMenuPage
import com.omnidroid.app.mobile.shared.compose.sidemenu.SideMenuPageScope
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.shared.ra.RaAchievementCard
import com.omnidroid.app.shared.ra.RaGameSummaryHeader
import com.omnidroid.app.shared.ra.RaLeaderboardCard
import com.omnidroid.app.shared.ra.RetroAchievementsCoordinator
import com.omnidroid.lib.ra.RetroAchievementsEngine

@Composable
fun GameMenuAchievementsScreen(coordinator: RetroAchievementsCoordinator) {
    val engine = coordinator.engine
    val activeGame by engine.activeGame.collectAsState()
    val summary by engine.activeSummary.collectAsState()
    val richPresence by engine.richPresence.collectAsState()
    val sessionState by coordinator.hardcorePolicy.sessionState.collectAsState()
    val cardContainerColor = chromeColors.content.copy(alpha = 0.05f)

    val coreBuckets =
        remember(activeGame?.id, summary) {
            if (engine.isModuleLoaded()) {
                engine.getAchievements(category = RetroAchievementsEngine.ACHIEVEMENT_CATEGORY_CORE)
            } else {
                emptyList()
            }
        }
    val unofficialBuckets =
        remember(activeGame?.id, summary) {
            if (engine.isModuleLoaded()) {
                engine.getAchievements(category = RetroAchievementsEngine.ACHIEVEMENT_CATEGORY_UNOFFICIAL)
            } else {
                emptyList()
            }
        }
    val leaderboardBuckets =
        remember(activeGame?.id, summary) {
            if (engine.isModuleLoaded()) {
                engine.getLeaderboards()
            } else {
                emptyList()
            }
        }

    var memoryDumpText by remember { mutableStateOf<String?>(null) }

    memoryDumpText?.let { dump ->
        OmnidroidDialog(
            width = DialogWidth.Expanded,
            onDismissRequest = { memoryDumpText = null },
            title = { Text(stringResource(R.string.ra_menu_diagnostics_title)) },
            text = {
                SelectionContainer {
                    Text(
                        text = dump,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            },
            confirmButton = {
                OmnidroidDialogButton(onClick = { memoryDumpText = null }) {
                    Text(stringResource(R.string.settings_action_close))
                }
            },
        )
    }

    SideMenuPage("game-achievements") {
        MenuSection("ra-summary") {
            val gameInfo = activeGame
            if (gameInfo == null) {
                MenuAction(
                    id = "ra-empty",
                    title = { Text(stringResource(R.string.ra_menu_achievements)) },
                    subtitle = {
                        Text(
                            stringResource(
                                if (!sessionState.coreSupported && sessionState.raEnabled) {
                                    R.string.ra_menu_core_unsupported
                                } else {
                                    R.string.ra_menu_no_active_game
                                },
                            ),
                        )
                    },
                    icon = { Icon(Icons.Outlined.EmojiEvents, contentDescription = null) },
                ) {}
            } else {
                val modeLabel =
                    stringResource(
                        if (coordinator.hardcorePolicy.isHardcoreActive) {
                            R.string.ra_menu_hardcore_badge
                        } else {
                            R.string.ra_menu_softcore_badge
                        },
                    )
                val scoreText =
                    summary?.let { s ->
                        stringResource(
                            R.string.ra_details_score_summary,
                            s.numUnlockedAchievements,
                            s.numCoreAchievements,
                            s.pointsUnlocked,
                            s.pointsCore,
                        )
                    }
                RaGameSummaryHeader(
                    badgeUrl = gameInfo.badgeUrl,
                    title = gameInfo.title,
                    scoreSummary = scoreText,
                    statusText = modeLabel,
                    richPresence = richPresence,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (engine.isModuleLoaded()) {
                MenuAction(
                    id = "ra-diagnostics",
                    titleRes = R.string.ra_menu_diagnostics_button,
                    icon = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                ) {
                    memoryDumpText = engine.dumpMemoryRegions()
                }
            }
        }

        GameMenuCardSection(
            id = "ra-core-achievements",
            titleRes = R.string.ra_menu_core_section,
            items = coreBuckets.flatMap { it.achievements },
        ) { ach ->
            RaAchievementCard(
                achievement = ach,
                useHardcoreRarity = coordinator.hardcorePolicy.isHardcoreActive,
                dimLockedBadge = true,
                containerColor = cardContainerColor,
            )
        }

        GameMenuCardSection(
            id = "ra-unofficial-achievements",
            titleRes = R.string.ra_menu_unofficial_section,
            items = unofficialBuckets.flatMap { it.achievements },
        ) { ach ->
            RaAchievementCard(
                achievement = ach,
                showRarity = false,
                containerColor = cardContainerColor,
            )
        }

        GameMenuCardSection(
            id = "ra-leaderboards",
            titleRes = R.string.ra_menu_leaderboards_section,
            items = leaderboardBuckets.flatMap { it.leaderboards },
        ) { lb ->
            RaLeaderboardCard(
                leaderboard = lb,
                showTracker = true,
                containerColor = cardContainerColor,
            )
        }
    }
}

@Composable
private fun <T> SideMenuPageScope.GameMenuCardSection(
    id: String,
    @StringRes titleRes: Int,
    items: List<T>,
    itemContent: @Composable (T) -> Unit,
) {
    MenuSection(
        id = id,
        visible = items.isNotEmpty(),
        titleRes = titleRes,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { item ->
                itemContent(item)
            }
        }
    }
}
