package com.omnidroid.app.mobile.feature.settings.general

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.navigation.NavController
import com.omnidroid.app.mobile.shared.compose.ui.homeChromeBackground
import com.omnidroid.app.mobile.shared.compose.ui.hugDisplayCutout
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.lib.library.MetaSystemID
import kotlinx.coroutines.yield

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
    navController: NavController,
    availableConsoles: List<MetaSystemID> = emptyList(),
) {
    val state =
        viewModel.uiState
            .collectAsState(SettingsViewModel.State())
            .value

    val scanInProgress =
        viewModel.directoryScanInProgress
            .collectAsState(false)
            .value

    val indexingInProgress =
        viewModel.indexingInProgress
            .collectAsState(false)
            .value

    val cacheState by viewModel.cacheState.collectAsState()

    var section by remember { mutableStateOf(SettingsSection.LIBRARY) }
    var path by remember { mutableStateOf(SettingsBackStack<SettingsSubscreen>()) }
    val activeSubscreen = path.current
    var pendingContentFocus by remember { mutableStateOf(true) }
    val contentFocusRequester = remember { FocusRequester() }
    val sidebarFocusRequesters =
        remember {
            SettingsSection.values().associateWith { FocusRequester() }
        }
    val controllerNav = LocalControllerNavigation.current

    val cycleSection by rememberUpdatedState { delta: Int ->
        val values = SettingsSection.values()
        val next = values[(values.indexOf(section) + delta).mod(values.size)]
        section = next
        path = SettingsBackStack()
        pendingContentFocus = true
    }

    BackHandler(enabled = activeSubscreen != null) {
        path = path.pop()
        pendingContentFocus = true
    }

    DisposableEffect(controllerNav, activeSubscreen) {
        val prevBack = controllerNav?.onBack
        controllerNav?.onCycleSettings = { cycleSection(it) }
        if (activeSubscreen != null) {
            controllerNav?.onBack = {
                path = path.pop()
                pendingContentFocus = true
            }
        }
        onDispose {
            controllerNav?.onCycleSettings = null
            if (activeSubscreen != null) {
                controllerNav?.onBack = prevBack ?: {}
            }
        }
    }

    LaunchedEffect(section, activeSubscreen, pendingContentFocus) {
        if (!pendingContentFocus) return@LaunchedEffect
        pendingContentFocus = false
        yield()
        runCatching { contentFocusRequester.requestFocus() }
    }

    val content: @Composable (Modifier) -> Unit = { paneModifier ->
        SettingsContentPane(
            section = section,
            activeSubscreen = activeSubscreen,
            parentTitleId = path.parent?.titleId ?: section.titleId,
            onNavigateSubscreen = {
                path = path.push(it)
                pendingContentFocus = true
            },
            onBackToSection = {
                path = path.pop()
                pendingContentFocus = true
            },
            state = state,
            cacheState = cacheState,
            scanInProgress = scanInProgress,
            indexingInProgress = indexingInProgress,
            viewModel = viewModel,
            navController = navController,
            availableConsoles = availableConsoles,
            modifier = paneModifier,
        )
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .homeChromeBackground(),
    ) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .hugDisplayCutout(WindowInsetsSides.Horizontal),
            ) {
                SettingsSidebar(
                    modifier =
                        Modifier
                            .focusGroup()
                            .focusProperties { right = contentFocusRequester },
                    selected = section,
                    focusRequesters = sidebarFocusRequesters,
                    onFocused = {
                        if (section != it) {
                            section = it
                            path = SettingsBackStack()
                        }
                    },
                    onActivate = {
                        section = it
                        path = SettingsBackStack()
                        pendingContentFocus = true
                    },
                )
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .focusGroup()
                            .focusProperties {
                                left = sidebarFocusRequesters.getValue(section)
                            },
                ) {
                    val paneModifier = Modifier.fillMaxSize().focusRequester(contentFocusRequester)
                    content(paneModifier)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                SettingsTopMenu(
                    modifier =
                        Modifier
                            .focusGroup()
                            .focusProperties { down = contentFocusRequester },
                    selected = section,
                    focusRequesters = sidebarFocusRequesters,
                    onFocused = {
                        if (section != it) {
                            section = it
                            path = SettingsBackStack()
                        }
                    },
                    onActivate = {
                        section = it
                        path = SettingsBackStack()
                        pendingContentFocus = true
                    },
                )
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .focusGroup()
                            .focusProperties {
                                up = sidebarFocusRequesters.getValue(section)
                            },
                ) {
                    val paneModifier = Modifier.fillMaxSize().focusRequester(contentFocusRequester)
                    content(paneModifier)
                }
            }
        }
    }
}
