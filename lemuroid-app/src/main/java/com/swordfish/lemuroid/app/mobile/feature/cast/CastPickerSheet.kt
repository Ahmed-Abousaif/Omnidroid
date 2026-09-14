package com.swordfish.lemuroid.app.mobile.feature.cast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.outlined.SettingsInputHdmi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.swordfish.lemuroid.app.shared.cast.CastState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastPickerSheet(
    state: CastState,
    onSelectDisplay: (Int) -> Unit,
    onStopCasting: () -> Unit,
    onFindDisplay: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.cast_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.cast_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            CastChoiceRow(
                selected = !state.isCasting,
                label = stringResource(R.string.cast_this_device),
                icon = { Icon(Icons.Filled.Cast, contentDescription = null, modifier = Modifier.size(22.dp)) },
                onClick = onStopCasting,
            )
            if (state.targets.isEmpty()) {
                Text(
                    text = stringResource(R.string.cast_no_displays),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                state.targets.forEach { target ->
                    CastChoiceRow(
                        selected = state.selectedDisplayId == target.displayId,
                        label = target.name,
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.CastConnected,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint =
                                    if (state.selectedDisplayId == target.displayId) {
                                        LibraryNeonGreen
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        },
                        onClick = { onSelectDisplay(target.displayId) },
                    )
                }
            }
            TextButton(onClick = onFindDisplay, modifier = Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.SettingsInputHdmi,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.cast_find_display),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CastChoiceRow(
    selected: Boolean,
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp, end = 12.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
        icon()
    }
}
