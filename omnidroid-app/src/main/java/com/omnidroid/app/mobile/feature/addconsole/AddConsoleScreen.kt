package com.omnidroid.app.mobile.feature.addconsole

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.addconsole.AddConsoleViewModel.ConsoleItem
import com.omnidroid.app.mobile.feature.library.LibrarySearchField
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogButton
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.compose.ui.LocalAdaptiveLayout
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.mobile.shared.compose.ui.homeChromeBackground
import com.omnidroid.app.mobile.shared.compose.ui.homeChromeColor
import com.omnidroid.app.mobile.shared.compose.ui.hugDisplayCutout
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.lib.library.MetaSystemID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal val ConsoleControlShape = RoundedCornerShape(12.dp)

private enum class ConfirmAction { DELETE_DATABASE, REMOVE_CONSOLE }

private data class ConsoleConfirmation(val console: ConsoleItem, val action: ConfirmAction)

@Composable
fun AddConsoleScreen(
    modifier: Modifier = Modifier,
    viewModel: AddConsoleViewModel,
) {
    val state by viewModel.state.collectAsState()
    ConsoleManagementContent(
        state = state,
        onInstall = viewModel::install,
        onRefetchDatabase = viewModel::refetchDatabase,
        onDeleteDatabase = viewModel::deleteDatabase,
        onRemoveConsole = viewModel::removeConsole,
        onConsumeError = viewModel::consumeError,
        modifier = modifier,
    )
}

@Composable
internal fun ConsoleManagementContent(
    state: AddConsoleViewModel.UiState,
    onInstall: (MetaSystemID) -> Unit,
    onRefetchDatabase: (MetaSystemID) -> Unit,
    onDeleteDatabase: (MetaSystemID) -> Unit,
    onRemoveConsole: (MetaSystemID) -> Unit,
    onConsumeError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<MetaSystemID?>(null) }
    var detailOpen by rememberSaveable { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<ConsoleConfirmation?>(null) }
    val context = LocalContext.current
    val catalogue =
        remember(state.consoles, query, context) {
            consoleCatalogue(state.consoles, query) { context.getString(it.titleResId) }
        }
    val resolvedId = resolveConsoleSelection(selectedId, if (detailOpen) state.consoles else catalogue)
    val selected = state.consoles.firstOrNull { it.metaSystemID == resolvedId }
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val navigation = LocalControllerNavigation.current
    val focusManager = LocalFocusManager.current
    val inputMode = LocalInputModeManager.current
    val scope = rememberCoroutineScope()
    var navigationJob by remember { mutableStateOf<Job?>(null) }
    val requesters = remember { MetaSystemID.values().associateWith { FocusRequester() } }
    val detailFocus = remember { FocusRequester() }
    val busy = state.busyMetaSystemID != null

    LaunchedEffect(resolvedId) { selectedId = resolvedId }
    LaunchedEffect(query) {
        navigationJob?.cancel()
        listState.scrollToItem(0)
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            onConsumeError()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().homeChromeBackground().hugDisplayCutout(WindowInsetsSides.Horizontal)) {
        val split = useConsoleSplitLayout(maxWidth.value, maxHeight.value)
        val showDetail = selected != null && (split || detailOpen)
        val small = maxHeight < 420.dp
        val catalogueWidth = (maxWidth * 0.38f).coerceIn(220.dp, 340.dp)
        BackHandler(enabled = detailOpen && !split) { detailOpen = false }

        val cycle by rememberUpdatedState { direction: Int ->
            if (catalogue.isNotEmpty() && confirmation == null) {
                val index = catalogue.indexOfFirst { it.metaSystemID == resolvedId }.coerceAtLeast(0)
                val next = (index + direction).coerceIn(0, catalogue.lastIndex)
                selectedId = catalogue[next].metaSystemID
                inputMode.requestInputMode(InputMode.Keyboard)
                navigationJob?.cancel()
                navigationJob =
                    scope.launch {
                        if (split || !detailOpen) {
                            listState.scrollToItem(next)
                            val targetId = catalogue[next].metaSystemID
                            snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.key == targetId.name } }
                                .first { it }
                            runCatching { requesters[targetId]?.requestFocus() }
                        } else {
                            runCatching { detailFocus.requestFocus() }
                        }
                    }
            }
        }
        DisposableEffect(navigation) {
            navigation?.onCarouselPrev = { cycle(-1) }
            navigation?.onCarouselNext = { cycle(1) }
            onDispose {
                navigation?.onCarouselPrev = {}
                navigation?.onCarouselNext = {}
            }
        }
        LaunchedEffect(showDetail, split) {
            if (showDetail && !split) runCatching { detailFocus.requestFocus() }
        }

        Row(
            Modifier.fillMaxSize().padding(horizontal = if (small) 12.dp else 20.dp)
                .padding(top = 6.dp, bottom = 8.dp),
        ) {
            if (split || !showDetail) {
                Column(
                    modifier = if (split) Modifier.width(catalogueWidth).fillMaxHeight() else Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().background(homeChromeColor).padding(bottom = 8.dp),
                    ) {
                        LibrarySearchField(
                            query = query,
                            onQueryChange = {
                                query = it
                                detailOpen = false
                            },
                            onClear = { query = "" },
                            onFocusChange = { navigation?.textInputActive = it },
                            modifier = Modifier.fillMaxWidth().testTag("console-search"),
                            trailing = {
                                Text(
                                    stringResource(
                                        R.string.consoles_installed_count,
                                        state.consoles.count { it.coreInstalled },
                                        MetaSystemID.values().size,
                                    ),
                                    color = chromeColors.muted,
                                    fontSize = (11 * LocalAdaptiveLayout.current.uiScale).sp,
                                    maxLines = 1,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            },
                        )
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth().focusGroup().testTag("console-catalogue"),
                    ) {
                        when {
                            state.loading ->
                                item {
                                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = LibraryNeonGreen)
                                    }
                                }
                            catalogue.isEmpty() ->
                                item {
                                    Column(
                                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Icon(Icons.Outlined.Search, null, Modifier.size(32.dp), tint = chromeColors.muted)
                                        Text(stringResource(R.string.consoles_no_results), color = chromeColors.content)
                                        TextButton(onClick = {
                                            query = ""
                                        }) {
                                            Text(stringResource(R.string.consoles_reset_search))
                                        }
                                    }
                                }
                            else ->
                                items(catalogue, key = { it.metaSystemID.name }) { console ->
                                    ConsoleCatalogueRow(
                                        console = console,
                                        selected = console.metaSystemID == resolvedId,
                                        busy = console.metaSystemID == state.busyMetaSystemID,
                                        modifier =
                                            Modifier.testTag("console-${console.metaSystemID.name}")
                                                .focusRequester(requesters.getValue(console.metaSystemID))
                                                .onFocusChanged { if (it.isFocused) selectedId = console.metaSystemID },
                                        onClick = {
                                            focusManager.clearFocus()
                                            selectedId = console.metaSystemID
                                            detailOpen = true
                                        },
                                    )
                                }
                        }
                    }
                }
            }
            if (split) {
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp),
                    color = chromeColors.divider,
                )
            }
            if (showDetail) {
                ConsoleManagementDetail(
                    console = selected,
                    busyAction = state.busyAction.takeIf { state.busyMetaSystemID == selected.metaSystemID },
                    actionsEnabled = !busy,
                    onInstall = { onInstall(selected.metaSystemID) },
                    onRefetch = { onRefetchDatabase(selected.metaSystemID) },
                    onDelete = { confirmation = ConsoleConfirmation(selected, ConfirmAction.DELETE_DATABASE) },
                    onRemove = { confirmation = ConsoleConfirmation(selected, ConfirmAction.REMOVE_CONSOLE) },
                    modifier = Modifier.weight(1f).fillMaxHeight().testTag("console-detail"),
                    focusRequester = detailFocus,
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    confirmation?.let { pending ->
        val delete = pending.action == ConfirmAction.DELETE_DATABASE
        OmnidroidDialog(
            destructive = true,
            onDismissRequest = { confirmation = null },
            title = {
                Text(
                    stringResource(
                        if (delete) R.string.consoles_delete_database_title else R.string.consoles_remove_console_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        when {
                            delete -> R.string.consoles_delete_database_message
                            pending.console.coreBundled -> R.string.consoles_remove_console_bundled_message
                            else -> R.string.consoles_remove_console_message
                        },
                        stringResource(pending.console.metaSystemID.titleResId),
                    ),
                )
            },
            confirmButton = {
                OmnidroidDialogButton(
                    enabled = !busy,
                    onClick = {
                        confirmation = null
                        if (delete) {
                            onDeleteDatabase(
                                pending.console.metaSystemID,
                            )
                        } else {
                            onRemoveConsole(pending.console.metaSystemID)
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (delete) R.string.consoles_confirm_delete else R.string.consoles_confirm_remove,
                        ),
                    )
                }
            },
            dismissButton = {
                OmnidroidDialogButton(
                    onClick = { confirmation = null },
                ) { Text(stringResource(R.string.consoles_cancel)) }
            },
        )
    }
}
