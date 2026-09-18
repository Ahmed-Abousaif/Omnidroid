package com.omnidroid.app.mobile.feature.settings.general

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavController
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.main.MainRoute
import com.omnidroid.app.mobile.feature.main.navigateToRoute
import com.omnidroid.app.mobile.shared.compose.ui.HomeChromeBackground
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow
import com.omnidroid.app.shared.library.LibraryIndexScheduler
import com.omnidroid.app.shared.settings.rememberHdModeAllowed
import com.omnidroid.app.utils.android.settings.OmnidroidCardSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsList
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsMenuLink
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsPage
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSlider
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSwitch
import com.omnidroid.app.utils.android.settings.booleanPreferenceState
import com.omnidroid.app.utils.android.settings.indexPreferenceState
import com.omnidroid.app.utils.android.settings.intPreferenceState
import com.omnidroid.app.utils.android.stringListResource
import kotlinx.coroutines.yield

private val SettingsSidebarWidth = 220.dp
private val SettingsCardHeight = 96.dp
private val SettingsCardShape = RoundedCornerShape(8.dp)
private val SettingsCardColor = Color(0xFF242424)

private enum class SettingsSection(
    @StringRes val titleId: Int,
) {
    GENERAL(R.string.settings_category_general),
    LIBRARY(R.string.settings_category_library),
    DISPLAY(R.string.settings_category_display),
    CONTROLLERS(R.string.settings_category_controllers),
    SAVES(R.string.settings_category_saves),
    ADVANCED(R.string.settings_title_advanced_settings),
    ABOUT(R.string.settings_category_about),
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
    navController: NavController,
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

    var section by remember { mutableStateOf(SettingsSection.GENERAL) }
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
        pendingContentFocus = true
    }

    DisposableEffect(controllerNav) {
        controllerNav?.onCycleSettings = { cycleSection(it) }
        onDispose { controllerNav?.onCycleSettings = null }
    }

    LaunchedEffect(section, pendingContentFocus) {
        if (!pendingContentFocus) return@LaunchedEffect
        pendingContentFocus = false
        yield()
        runCatching { contentFocusRequester.requestFocus() }
    }

    Row(
        modifier =
            modifier
                .fillMaxSize()
                .background(HomeChromeBackground),
    ) {
        SettingsSidebar(
            modifier =
                Modifier
                    .focusGroup()
                    .focusProperties { right = contentFocusRequester },
            selected = section,
            focusRequesters = sidebarFocusRequesters,
            onFocused = { section = it },
            onActivate = {
                section = it
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
            when (section) {
                SettingsSection.GENERAL ->
                    GeneralHub(
                        modifier = paneModifier,
                        onSelectSection = {
                            section = it
                            pendingContentFocus = true
                        },
                    )
                SettingsSection.LIBRARY ->
                    OmnidroidSettingsPage(modifier = paneModifier) {
                        RomsSettings(
                            state = state,
                            onChangeFolder = { viewModel.changeLocalStorageFolder() },
                            indexingInProgress = indexingInProgress,
                            scanInProgress = scanInProgress,
                        )
                    }
                SettingsSection.DISPLAY ->
                    OmnidroidSettingsPage(modifier = paneModifier) { DisplaySettings() }
                SettingsSection.CONTROLLERS ->
                    OmnidroidSettingsPage(modifier = paneModifier) {
                        ControllerSettings(navController = navController)
                    }
                SettingsSection.SAVES ->
                    OmnidroidSettingsPage(modifier = paneModifier) {
                        SavesSettings(
                            isSaveSyncSupported = state.isSaveSyncSupported,
                            navController = navController,
                        )
                    }
                SettingsSection.ADVANCED ->
                    OmnidroidSettingsPage(modifier = paneModifier) {
                        AdvancedLinks(
                            indexingInProgress = indexingInProgress,
                            navController = navController,
                        )
                    }
                SettingsSection.ABOUT ->
                    OmnidroidSettingsPage(modifier = paneModifier) {
                        AboutSettings()
                    }
            }
        }
    }
}

@Composable
private fun SettingsSidebar(
    modifier: Modifier = Modifier,
    selected: SettingsSection,
    focusRequesters: Map<SettingsSection, FocusRequester>,
    onFocused: (SettingsSection) -> Unit,
    onActivate: (SettingsSection) -> Unit,
) {
    Column(
        modifier =
            modifier
                .width(SettingsSidebarWidth)
                .fillMaxHeight()
                .padding(top = 8.dp, bottom = 16.dp),
    ) {
        SettingsSection.values().forEach { item ->
            val isSelected = item == selected
            Surface(
                onClick = { onActivate(item) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequesters.getValue(item))
                        .onFocusChanged { if (it.isFocused) onFocused(item) },
                shape = RectangleShape,
                color = if (isSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent,
            ) {
                Row(
                    modifier = Modifier.height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(if (isSelected) LibraryNeonGreen else Color.Transparent),
                    )
                    Text(
                        text = stringResource(item.titleId),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.padding(start = 16.dp, end = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneralHub(
    modifier: Modifier = Modifier,
    onSelectSection: (SettingsSection) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsHubCard(
                title = stringResource(R.string.settings_category_library),
                icon = Icons.Outlined.Folder,
                onClick = { onSelectSection(SettingsSection.LIBRARY) },
            )
        }
        item {
            SettingsHubCard(
                title = stringResource(R.string.settings_category_display),
                icon = Icons.Outlined.Monitor,
                onClick = { onSelectSection(SettingsSection.DISPLAY) },
            )
        }
        item {
            SettingsHubCard(
                title = stringResource(R.string.settings_category_controllers),
                icon = Icons.Outlined.SportsEsports,
                onClick = { onSelectSection(SettingsSection.CONTROLLERS) },
            )
        }
        item {
            SettingsHubCard(
                title = stringResource(R.string.settings_category_saves),
                icon = Icons.Outlined.CloudSync,
                onClick = { onSelectSection(SettingsSection.SAVES) },
            )
        }
        item {
            SettingsHubCard(
                title = stringResource(R.string.settings_title_advanced_settings),
                icon = Icons.Outlined.Tune,
                onClick = { onSelectSection(SettingsSection.ADVANCED) },
            )
        }
        item {
            SettingsHubCard(
                title = stringResource(R.string.settings_category_about),
                icon = Icons.Outlined.Info,
                onClick = { onSelectSection(SettingsSection.ABOUT) },
            )
        }
    }
}

@Composable
private fun SettingsHubCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .height(SettingsCardHeight)
                .controllerFocusGlow(SettingsCardShape),
        shape = SettingsCardShape,
        color = SettingsCardColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

@Composable
private fun AdvancedLinks(
    indexingInProgress: Boolean,
    navController: NavController,
) {
    OmnidroidCardSettingsGroup {
        OmnidroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_open_cores_selection)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_open_cores_selection))
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_CORES_SELECTION) },
        )
        OmnidroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_display_bios_info)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_display_bios_info))
            },
            enabled = !indexingInProgress,
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_BIOS) },
        )
        OmnidroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_device_profile)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_device_profile))
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_DEVICE_PROFILE) },
        )
        OmnidroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_advanced_settings)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_advanced_settings))
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_ADVANCED) },
        )
    }
}

@Composable
private fun AboutSettings() {
    val context = LocalContext.current

    OmnidroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_section_privacy)) },
    ) {
        OmnidroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_privacy_policy)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_privacy_policy))
            },
            onClick = { openExternalUrl(context, context.getString(R.string.url_privacy_policy)) },
        )
    }

    OmnidroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_section_donations)) },
    ) {
        OmnidroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_donate_buy_me_a_coffee)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_donate_buy_me_a_coffee))
            },
            onClick = {
                openExternalUrl(context, context.getString(R.string.url_donate_buy_me_a_coffee))
            },
        )
        OmnidroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_donate_ko_fi)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_donate_ko_fi))
            },
            onClick = { openExternalUrl(context, context.getString(R.string.url_donate_ko_fi)) },
        )
    }

    OmnidroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_section_about_developer)) },
    ) {
        OmnidroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_about_developer)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_about_developer))
            },
            onClick = { openExternalUrl(context, context.getString(R.string.url_developer_github)) },
        )
        OmnidroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_developer_github)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_developer_github))
            },
            onClick = { openExternalUrl(context, context.getString(R.string.url_developer_github)) },
        )
    }
}

private fun openExternalUrl(
    context: Context,
    url: String,
) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
private fun SavesSettings(
    isSaveSyncSupported: Boolean,
    navController: NavController,
) {
    OmnidroidCardSettingsGroup {
        OmnidroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_autosave, true),
            title = { Text(text = stringResource(id = R.string.settings_title_enable_autosave)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_autosave)) },
        )
        if (isSaveSyncSupported) {
            OmnidroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.settings_title_save_sync)) },
                subtitle = {
                    Text(text = stringResource(id = R.string.settings_description_save_sync))
                },
                onClick = { navController.navigateToRoute(MainRoute.SETTINGS_SAVE_SYNC) },
            )
        }
    }
}

@Composable
private fun ControllerSettings(navController: NavController) {
    OmnidroidCardSettingsGroup {
        OmnidroidSettingsList(
            state =
                indexPreferenceState(
                    R.string.pref_key_haptic_feedback_mode,
                    "press",
                    stringListResource(R.array.pref_key_haptic_feedback_mode_values),
                ),
            title = {
                Text(text = stringResource(id = R.string.settings_title_enable_touch_feedback))
            },
            items = stringListResource(R.array.pref_key_haptic_feedback_mode_display_names),
        )
        OmnidroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_gamepad_settings)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_gamepad_settings))
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_INPUT_DEVICES) },
        )
        val rumbleEnabled = booleanPreferenceState(R.string.pref_key_enable_rumble, false)
        OmnidroidSettingsSwitch(
            state = rumbleEnabled,
            title = { Text(text = stringResource(id = R.string.settings_title_enable_rumble)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_rumble)) },
        )
        OmnidroidSettingsSwitch(
            enabled = rumbleEnabled.value,
            state = booleanPreferenceState(R.string.pref_key_enable_device_rumble, false),
            title = { Text(text = stringResource(id = R.string.settings_title_enable_device_rumble)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_device_rumble)) },
        )
        OmnidroidSettingsSlider(
            state =
                intPreferenceState(
                    key = stringResource(id = R.string.pref_key_vibration_intensity),
                    default = 50,
                ),
            steps = 100,
            valueRange = 0f..100f,
            enabled = true,
            title = { Text(text = stringResource(R.string.settings_title_vibration_intensity)) },
            subtitle = { Text(text = stringResource(R.string.settings_description_vibration_intensity)) },
            valueText = { value ->
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
        )
        OmnidroidSettingsSlider(
            state =
                intPreferenceState(
                    key = stringResource(id = R.string.pref_key_tilt_sensitivity_index),
                    default = 6,
                ),
            steps = 10,
            valueRange = 0f..10f,
            enabled = true,
            title = { Text(text = stringResource(R.string.settings_title_tilt_sensitivity)) },
        )
    }
}

@Composable
private fun DisplaySettings() {
    val hdMode = booleanPreferenceState(R.string.pref_key_hd_mode, false)
    val immersiveMode = booleanPreferenceState(R.string.pref_key_enable_immersive_mode, false)
    val hdModeAllowed = rememberHdModeAllowed()

    LaunchedEffect(hdModeAllowed) {
        if (!hdModeAllowed && hdMode.value) {
            hdMode.value = false
        }
    }

    OmnidroidCardSettingsGroup {
        OmnidroidSettingsSwitch(
            state = immersiveMode,
            title = { Text(text = stringResource(id = R.string.settings_title_immersive_mode)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_immersive_mode)) },
        )
        OmnidroidSettingsSwitch(
            enabled = hdModeAllowed,
            state = hdMode,
            title = { Text(text = stringResource(id = R.string.settings_title_hd_mode)) },
            subtitle = {
                Text(
                    text =
                        stringResource(
                            if (hdModeAllowed) {
                                R.string.settings_description_hd_mode
                            } else {
                                R.string.settings_description_hd_mode_battery
                            },
                        ),
                )
            },
            onCheckedChange = { enabled ->
                if (!hdModeAllowed && enabled) {
                    hdMode.value = false
                }
            },
        )
        OmnidroidSettingsSlider(
            enabled = hdMode.value && hdModeAllowed,
            state =
                intPreferenceState(
                    key = stringResource(id = R.string.pref_key_hd_mode_quality),
                    default = 2,
                ),
            steps = 1,
            valueRange = 0f..2f,
            title = { Text(text = stringResource(R.string.settings_title_hd_quality)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_hd_quality)) },
        )
        OmnidroidSettingsList(
            enabled = !hdMode.value,
            state =
                indexPreferenceState(
                    R.string.pref_key_shader_filter,
                    "auto",
                    stringListResource(R.array.pref_key_shader_filter_values).toList(),
                ),
            title = { Text(text = stringResource(id = R.string.display_filter)) },
            items = stringListResource(R.array.pref_key_shader_filter_display_names),
        )
    }
}

@Composable
private fun RomsSettings(
    state: SettingsViewModel.State,
    onChangeFolder: () -> Unit,
    indexingInProgress: Boolean,
    scanInProgress: Boolean,
) {
    val context = LocalContext.current

    val currentDirectory = state.currentDirectory
    val emptyDirectory = stringResource(R.string.none)

    val currentDirectoryName =
        remember(state.currentDirectory) {
            runCatching {
                DocumentFile.fromTreeUri(context, Uri.parse(currentDirectory))?.name
            }.getOrNull() ?: emptyDirectory
        }

    OmnidroidCardSettingsGroup {
        OmnidroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.directory)) },
            subtitle = { Text(text = currentDirectoryName) },
            onClick = { onChangeFolder() },
            enabled = !indexingInProgress,
        )
        if (scanInProgress) {
            OmnidroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.stop)) },
                onClick = { LibraryIndexScheduler.cancelLibrarySync(context) },
            )
        } else {
            OmnidroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.rescan)) },
                onClick = { LibraryIndexScheduler.scheduleLibrarySync(context) },
                enabled = !indexingInProgress,
            )
        }
    }
}
