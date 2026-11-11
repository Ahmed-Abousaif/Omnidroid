package com.omnidroid.app.utils.android.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.storage.base.SettingValueState
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OmnidroidSettingsList(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: SettingValueState<Int>? = null,
    selectedIndex: Int = state?.value ?: -1,
    onSelectionChange: (Int) -> Unit = { state?.value = it },
    titleRes: Int? = null,
    title: @Composable () -> Unit = { Text(stringResource(requireNotNull(titleRes))) },
    items: List<String>,
    icon: (@Composable () -> Unit)? = null,
    useSelectedValueAsSubtitle: Boolean = true,
    subtitleRes: Int? = null,
    subtitle: (@Composable () -> Unit)? = subtitleRes?.let { { Text(stringResource(it)) } },
    closeDialogDelay: Long = 200,
    action: (@Composable () -> Unit)? = null,
    onItemSelected: ((Int, String) -> Unit)? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    var pendingSelection by remember(items) { mutableStateOf<Int?>(null) }

    val safeSubtitle =
        if (selectedIndex in items.indices && useSelectedValueAsSubtitle) {
            { Text(text = items[selectedIndex]) }
        } else {
            subtitle
        }

    OmnidroidSettingsMenuLink(
        modifier = modifier,
        enabled = enabled && items.isNotEmpty(),
        icon = icon,
        title = title,
        subtitle = safeSubtitle,
        action = action,
        onClick = { pendingSelection = null; showDialog = true },
    )

    if (!showDialog) return

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val onSelected: (Int, Boolean) -> Unit = { selectedIndex, updateState ->
        pendingSelection = selectedIndex
        coroutineScope.launch {
            if (updateState) onSelectionChange(selectedIndex)
            delay(closeDialogDelay)
            showDialog = false
        }
    }

    AlertDialog(
        title = title,
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .selectableGroup(),
            ) {
                if (subtitle != null) {
                    subtitle()
                    Spacer(modifier = Modifier.size(8.dp))
                }

                items.forEachIndexed { index, item ->
                    val isSelected by rememberUpdatedState(newValue = (pendingSelection ?: selectedIndex) == index)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .controllerFocusGlow(RoundedCornerShape(8.dp))
                                .selectable(
                                    role = Role.RadioButton,
                                    selected = isSelected,
                                    onClick = {
                                        onSelected(index, !isSelected)
                                        onItemSelected?.invoke(index, items[index])
                                    },
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        },
        onDismissRequest = { showDialog = false },
        confirmButton = {},
        dismissButton = {},
    )
}
