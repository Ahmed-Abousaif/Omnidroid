package com.omnidroid.app.mobile.feature.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow
import com.omnidroid.lib.library.MetaSystemID

/** Human readable name of a startup destination, e.g. "Favorites" or "PlayStation Portable". */
@Composable
fun startupDestinationLabel(filter: LibraryFilter): String =
    when (filter) {
        is LibraryFilter.All -> stringResource(R.string.settings_startup_full_library)
        is LibraryFilter.Favorites -> stringResource(R.string.settings_startup_favorites)
        is LibraryFilter.System -> stringResource(id = filter.metaSystemID.titleResId)
    }

@Composable
private fun StartupDestinationIcon(filter: LibraryFilter) {
    Box(modifier = Modifier.padding(start = 16.dp)) {
        when (filter) {
            is LibraryFilter.All ->
                Icon(
                    imageVector = Icons.Filled.GridView,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = LibraryNeonGreen,
                )
            is LibraryFilter.Favorites ->
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = LibraryNeonGreen,
                )
            is LibraryFilter.System ->
                Image(
                    painter = painterResource(id = filter.metaSystemID.imageResId),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(CircleShape),
                    contentScale = ContentScale.Fit,
                )
        }
    }
}

@Composable
fun SetStartupScreenDialog(
    console: MetaSystemID,
    isStartupScreen: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val consoleName = stringResource(id = console.titleResId)

    AlertDialog(
        title = { Text(text = consoleName) },
        text = {
            Text(text = stringResource(R.string.settings_startup_dialog_body, consoleName))
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                modifier = Modifier.controllerFocusGlow(),
                onClick = onConfirm,
            ) {
                Text(
                    text =
                        stringResource(
                            if (isStartupScreen) {
                                R.string.settings_action_clear_startup
                            } else {
                                R.string.settings_action_set_startup
                            },
                        ),
                    color = LibraryNeonGreen,
                )
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.controllerFocusGlow(),
                onClick = onDismiss,
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun StartupDestinationPickerDialog(
    destinations: List<LibraryFilter>,
    selected: LibraryFilter,
    onSelected: (LibraryFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = { Text(text = stringResource(R.string.settings_title_startup_destination)) },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .selectableGroup(),
            ) {
                Text(
                    text = stringResource(R.string.settings_description_startup_destination),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                destinations.forEach { destination ->
                    StartupDestinationRow(
                        filter = destination,
                        selected = destination == selected,
                        onClick = { onSelected(destination) },
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(
                modifier = Modifier.controllerFocusGlow(),
                onClick = onDismiss,
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun StartupDestinationRow(
    filter: LibraryFilter,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .controllerFocusGlow(RoundedCornerShape(8.dp))
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        StartupDestinationIcon(filter)
        Text(
            text = startupDestinationLabel(filter),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

/** The settings list: full library, favorites, then one entry per installed console. */
fun startupDestinations(consoles: List<MetaSystemID>): List<LibraryFilter> =
    buildList {
        add(LibraryFilter.All)
        add(LibraryFilter.Favorites)
        consoles.forEach { add(LibraryFilter.System(it)) }
    }

