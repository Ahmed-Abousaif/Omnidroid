package com.omnidroid.app.mobile.feature.addconsole

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.addconsole.AddConsoleViewModel.BusyAction
import com.omnidroid.app.mobile.feature.addconsole.AddConsoleViewModel.ConsoleItem
import com.omnidroid.app.mobile.shared.compose.ui.BatteryLowRed
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow

private val ConsoleDangerRed = BatteryLowRed

@Composable
internal fun ConsoleManagementDetail(
    console: ConsoleItem,
    busyAction: BusyAction?,
    actionsEnabled: Boolean,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    onInstall: () -> Unit,
    onRefetch: () -> Unit,
    onDelete: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
) {
    val accent = Color(console.metaSystemID.color())
    val actionColor = lerp(chromeColors.panel, accent, 0.22f)
    key(console.metaSystemID) {
        Column(
            modifier.verticalScroll(rememberScrollState()).focusGroup().padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(listOf(lerp(chromeColors.panel, accent, 0.16f), chromeColors.panel)),
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showBack) {
                    DetailIconAction(
                        icon = Icons.AutoMirrored.Outlined.ArrowBack,
                        label = stringResource(R.string.consoles_back_catalogue),
                        enabled = true,
                        onClick = onBack,
                        modifier = Modifier.focusRequester(focusRequester),
                        tonal = false,
                    )
                }
                ConsoleArtwork(console, busyAction != null, Modifier.size(48.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        stringResource(console.metaSystemID.titleResId),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = chromeColors.content,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.consoles_library_count, console.libraryGameCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = chromeColors.muted,
                    )
                }
            }
            if (busyAction != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(busyAction.label()),
                        style = MaterialTheme.typography.bodySmall,
                        color = chromeColors.muted,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = LibraryNeonGreen)
                }
            } else if (!actionsEnabled) {
                Text(
                    stringResource(R.string.consoles_wait_for_action),
                    style = MaterialTheme.typography.bodySmall,
                    color = chromeColors.muted,
                )
            }
            HorizontalDivider(color = chromeColors.divider)
            DetailRow(
                icon = Icons.Outlined.SportsEsports,
                title = stringResource(R.string.consoles_emulator_section),
                value =
                    console.coreDisplayName?.let { name ->
                        stringResource(
                            R.string.consoles_emulator_summary,
                            name,
                            stringResource(
                                when {
                                    console.coreBundled -> R.string.consoles_emulator_builtin_short
                                    console.coreInstalled -> R.string.consoles_emulator_installed_short
                                    else -> R.string.consoles_emulator_missing_short
                                },
                            ),
                        )
                    } ?: stringResource(R.string.consoles_emulator_unavailable),
                actions = {
                    if (!console.coreInstalled || !console.registeredOrHasGames) {
                        FilledTonalButton(
                            onClick = onInstall,
                            enabled = actionsEnabled && console.coreDisplayName != null,
                            modifier = Modifier.controllerFocusGlow(ConsoleControlShape),
                            shape = ConsoleControlShape,
                            colors =
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = actionColor,
                                    contentColor = chromeColors.content,
                                ),
                        ) {
                            Text(
                                stringResource(
                                    when {
                                        console.coreInstalled -> R.string.consoles_add_library
                                        console.registeredOrHasGames -> R.string.consoles_repair_emulator
                                        else -> R.string.consoles_install_console
                                    },
                                ),
                            )
                        }
                    }
                },
            )
            HorizontalDivider(color = chromeColors.divider)
            DetailRow(
                icon = Icons.Outlined.Storage,
                title = stringResource(R.string.consoles_database_short),
                value =
                    stringResource(
                        when {
                            console.databaseInstalled -> R.string.consoles_database_titles
                            console.hasDatabase -> R.string.consoles_database_partial_titles
                            !console.databaseAvailable -> R.string.consoles_db_unavailable
                            else -> R.string.consoles_database_missing_status
                        },
                        console.databaseGameCount,
                    ),
                actions = {
                    if (console.databaseAvailable) {
                        DetailIconAction(
                            icon = if (console.databaseInstalled) Icons.Outlined.Refresh else Icons.Outlined.Download,
                            label =
                                stringResource(
                                    if (console.databaseInstalled) {
                                        R.string.consoles_refresh_database
                                    } else {
                                        R.string.consoles_download_database_full
                                    },
                                ),
                            enabled = actionsEnabled,
                            onClick = onRefetch,
                            containerColor = actionColor,
                        )
                        if (console.hasDatabase) {
                            DetailIconAction(
                                icon = Icons.Outlined.DeleteOutline,
                                label = stringResource(R.string.consoles_delete_database_full),
                                enabled = actionsEnabled,
                                onClick = onDelete,
                                containerColor = if (actionsEnabled) ConsoleDangerRed.copy(alpha = 0.16f) else chromeColors.panelSelected,
                                contentColor = if (actionsEnabled) ConsoleDangerRed else chromeColors.faint,
                            )
                        }
                    }
                },
            )
            if (console.canRemove) {
                HorizontalDivider(color = chromeColors.divider)
                TextButton(
                    onClick = onRemove,
                    enabled = actionsEnabled,
                    modifier = Modifier.align(Alignment.CenterHorizontally).controllerFocusGlow(ConsoleControlShape),
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = ConsoleDangerRed,
                            disabledContentColor = chromeColors.faint,
                        ),
                ) {
                    Text(
                        stringResource(R.string.consoles_remove_console_full),
                        color = if (actionsEnabled) ConsoleDangerRed else chromeColors.faint,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    title: String,
    value: String,
    actions: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.padding(end = 12.dp).size(20.dp), tint = chromeColors.muted)
        Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = chromeColors.muted)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = chromeColors.content)
        }
        actions()
    }
}

@Composable
private fun DetailIconAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tonal: Boolean = true,
    containerColor: Color = chromeColors.panelSelected,
    contentColor: Color = chromeColors.content,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp).controllerFocusGlow(ConsoleControlShape),
    ) {
        Box(
            Modifier.size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (tonal) containerColor else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, Modifier.size(18.dp), tint = if (enabled) contentColor else chromeColors.faint)
        }
    }
}

private fun BusyAction.label(): Int =
    when (this) {
        BusyAction.INSTALL -> R.string.consoles_setting_up
        BusyAction.REFETCH_DATABASE -> R.string.consoles_refreshing
        BusyAction.DELETE_DATABASE -> R.string.consoles_deleting
        BusyAction.REMOVE_CONSOLE -> R.string.consoles_removing
    }
