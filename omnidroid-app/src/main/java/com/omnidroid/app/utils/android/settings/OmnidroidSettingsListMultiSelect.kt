package com.omnidroid.app.utils.android.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.storage.base.SettingValueState
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogButton

@Composable
fun OmnidroidSettingsListMultiSelect(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: SettingValueState<Set<String>>,
    titleRes: Int? = null,
    title: @Composable () -> Unit = { Text(stringResource(requireNotNull(titleRes))) },
    entryValues: List<String>,
    entries: List<String>,
    icon: @Composable (() -> Unit)? = null,
    confirmButton: String,
    subtitleRes: Int? = null,
    subtitle: (@Composable () -> Unit)? = subtitleRes?.let { { Text(stringResource(it)) } },
    onItemsSelected: ((List<String>) -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
) {
    if (entryValues.size != entries.size) {
        throw IllegalArgumentException("entries and entryValues need to have the same size")
    }

    var showDialog by remember { mutableStateOf(false) }
    var pendingSelection by remember { mutableStateOf(state.value) }

    OmnidroidSettingsMenuLink(
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        title = title,
        subtitle = subtitle,
        action = action,
        onClick = { pendingSelection = state.value.toSet(); showDialog = true },
    )

    if (!showDialog) return

    val onAdd: (Int) -> Unit = { selectedIndex ->
        pendingSelection = pendingSelection + entryValues[selectedIndex]
    }
    val onRemove: (Int) -> Unit = { selectedIndex ->
        pendingSelection = pendingSelection - entryValues[selectedIndex]
    }

    OmnidroidDialog(
        title = title,
        text = {
            Column(
                modifier = Modifier,
            ) {
                if (subtitle != null) {
                    subtitle()
                    Spacer(modifier = Modifier.size(8.dp))
                }

                entryValues.forEachIndexed { index, item ->
                    val isSelected by rememberUpdatedState(newValue = pendingSelection.contains(item))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(
                                    role = Role.Checkbox,
                                    value = isSelected,
                                    onValueChange = {
                                        if (isSelected) {
                                            onRemove(index)
                                        } else {
                                            onAdd(index)
                                        }
                                    },
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entries[index],
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null,
                        )
                    }
                }
            }
        },
        onDismissRequest = { showDialog = false },
        confirmButton = {
            OmnidroidDialogButton(
                onClick = {
                    state.value = pendingSelection
                    showDialog = false
                    onItemsSelected?.invoke(entryValues.filter { pendingSelection.contains(it) })
                },
            ) {
                Text(text = confirmButton)
            }
        },
        dismissButton = {
            OmnidroidDialogButton(onClick = { showDialog = false }) { Text(stringResource(R.string.cancel)) }
        },
    )
}
