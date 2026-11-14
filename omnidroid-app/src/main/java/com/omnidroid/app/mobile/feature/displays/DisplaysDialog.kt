package com.omnidroid.app.mobile.feature.displays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogButton
import com.omnidroid.app.shared.displays.DisplayStatus
import com.omnidroid.app.shared.displays.DisplaysState

@Composable
fun DisplaysDialog(
    state: DisplaysState,
    onAutomaticChanged: (Boolean) -> Unit,
    onHiddenChanged: (Int, Boolean) -> Unit,
    onConnectionSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    OmnidroidDialog(
        onDismissRequest = onDismiss,
        scrollBody = false,
        title = { Text(stringResource(R.string.displays_title)) },
        dismissButton = {
            OmnidroidDialogButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.displays_description), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val label = stringResource(R.string.displays_automatic)
                    Text(label, modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.automatic,
                        onCheckedChange = onAutomaticChanged,
                        enabled = state.connected,
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
                if (!state.connected) {
                    Text(stringResource(R.string.displays_connecting))
                } else if (state.entries.isEmpty()) {
                    Text(stringResource(R.string.displays_none), style = MaterialTheme.typography.bodyMedium)
                }
                state.entries.forEach { entry ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(entry.facts.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(
                                    when (entry.status) {
                                        DisplayStatus.READY -> R.string.displays_status_ready
                                        DisplayStatus.SHOWING -> R.string.displays_status_showing
                                        DisplayStatus.SLEEPING -> R.string.displays_status_sleeping
                                        DisplayStatus.HIDDEN -> R.string.displays_status_hidden
                                        DisplayStatus.PAUSED -> R.string.displays_status_paused
                                        DisplayStatus.UNAVAILABLE -> R.string.displays_status_unavailable
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val dimensions =
                                stringResource(R.string.displays_dimensions, entry.facts.width, entry.facts.height)
                            Text(
                                dimensions,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (state.automatic && entry.status !in setOf(DisplayStatus.SLEEPING, DisplayStatus.PAUSED)) {
                            val hide = entry.status == DisplayStatus.SHOWING || entry.status == DisplayStatus.READY
                            OmnidroidDialogButton(onClick = { onHiddenChanged(entry.facts.id, hide) }) {
                                Text(
                                    stringResource(
                                        when {
                                            hide -> R.string.displays_hide
                                            entry.status == DisplayStatus.UNAVAILABLE -> R.string.displays_retry
                                            else -> R.string.displays_show
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.displays_connection_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OmnidroidDialogButton(onClick = onConnectionSettings) {
                    Text(stringResource(R.string.displays_connection_settings))
                }
            }
        },
    )
}
