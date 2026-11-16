package com.omnidroid.app.mobile.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.VideogameAssetOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.dialog.DialogWidth
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogButton
import com.omnidroid.app.mobile.shared.compose.ui.chromeMutedContentColor
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow
import com.omnidroid.lib.library.SystemID
import de.charlex.compose.material3.HtmlText

/** Why the library has nothing to show; each case gets its own message and actions. */
enum class LibraryEmptyKind {
    /** No games folder chosen yet. */
    NO_FOLDER,

    /** A folder is set but the scan found no games at all. */
    NO_GAMES,

    /** The selected console has no games. */
    NO_CONSOLE_GAMES,

    /** Favorites is selected and nothing is favorited. */
    NO_FAVORITES,

    /** A search matched nothing. */
    NO_SEARCH_RESULTS,
}

fun libraryEmptyKind(
    hasGamesFolder: Boolean,
    filter: LibraryFilter,
    searchQuery: String,
): LibraryEmptyKind =
    when {
        searchQuery.isNotBlank() -> LibraryEmptyKind.NO_SEARCH_RESULTS
        filter is LibraryFilter.Favorites -> LibraryEmptyKind.NO_FAVORITES
        !hasGamesFolder -> LibraryEmptyKind.NO_FOLDER
        filter is LibraryFilter.System -> LibraryEmptyKind.NO_CONSOLE_GAMES
        else -> LibraryEmptyKind.NO_GAMES
    }

@Composable
internal fun LibraryEmptyState(
    kind: LibraryEmptyKind,
    filter: LibraryFilter,
    searchQuery: String,
    scanning: Boolean,
    onPickFolder: () -> Unit,
    onRescan: () -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showHelp by remember { mutableStateOf(false) }
    if (showHelp) GamesFolderHelpDialog(onDismiss = { showHelp = false })

    val consoleName = (filter as? LibraryFilter.System)?.let { stringResource(it.metaSystemID.titleResId) }
    val (icon, title, body) =
        when (kind) {
            LibraryEmptyKind.NO_FOLDER ->
                Triple(
                    Icons.Outlined.FolderOpen,
                    stringResource(R.string.library_empty_no_folder_title),
                    stringResource(R.string.library_empty_no_folder_body),
                )
            LibraryEmptyKind.NO_GAMES ->
                Triple(
                    Icons.Outlined.VideogameAssetOff,
                    stringResource(R.string.library_empty_no_games_title),
                    stringResource(R.string.library_empty_no_games_body),
                )
            LibraryEmptyKind.NO_CONSOLE_GAMES ->
                Triple(
                    Icons.Outlined.VideogameAssetOff,
                    stringResource(R.string.library_empty_no_console_games_title, consoleName.orEmpty()),
                    stringResource(R.string.library_empty_no_games_body),
                )
            LibraryEmptyKind.NO_FAVORITES ->
                Triple(
                    Icons.Outlined.FavoriteBorder,
                    stringResource(R.string.library_empty_no_favorites_title),
                    stringResource(R.string.library_empty_no_favorites_body),
                )
            LibraryEmptyKind.NO_SEARCH_RESULTS ->
                Triple(
                    Icons.Outlined.SearchOff,
                    stringResource(R.string.library_empty_no_results_title, searchQuery.trim()),
                    stringResource(R.string.library_empty_no_results_body),
                )
        }

    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = chromeMutedContentColor)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = chromeMutedContentColor,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (kind) {
                    LibraryEmptyKind.NO_FOLDER -> {
                        PrimaryAction(R.string.library_pick_games_folder, enabled = !scanning, onClick = onPickFolder)
                        SecondaryAction(R.string.library_empty_folder_help) { showHelp = true }
                    }
                    LibraryEmptyKind.NO_GAMES, LibraryEmptyKind.NO_CONSOLE_GAMES -> {
                        PrimaryAction(
                            if (scanning) R.string.library_scanning else R.string.library_empty_rescan,
                            enabled = !scanning,
                            onClick = onRescan,
                        )
                        SecondaryAction(R.string.library_empty_change_folder, onClick = onPickFolder)
                        SecondaryAction(R.string.library_empty_folder_help) { showHelp = true }
                    }
                    LibraryEmptyKind.NO_SEARCH_RESULTS ->
                        PrimaryAction(R.string.library_empty_clear_search, onClick = onClearSearch)
                    LibraryEmptyKind.NO_FAVORITES -> Unit
                }
            }
        }
    }
}

@Composable
private fun PrimaryAction(
    textRes: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.controllerFocusGlow()) {
        Text(stringResource(textRes))
    }
}

@Composable
private fun SecondaryAction(
    textRes: Int,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.controllerFocusGlow()) {
        Text(stringResource(textRes))
    }
}

/** How to lay out the games folder (one subfolder per console, named after the system). */
@Composable
internal fun GamesFolderHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val message =
        remember {
            val systemFolders = SystemID.values().joinToString(", ") { "<i>${it.dbname}</i>" }
            context.getString(R.string.omnidroid_help_content).replace("\$SYSTEMS", systemFolders)
        }
    OmnidroidDialog(
        text = { HtmlText(text = message) },
        width = DialogWidth.Standard,
        onDismissRequest = onDismiss,
        confirmButton = {
            OmnidroidDialogButton(onClick = onDismiss) { Text(stringResource(R.string.settings_action_close)) }
        },
    )
}
