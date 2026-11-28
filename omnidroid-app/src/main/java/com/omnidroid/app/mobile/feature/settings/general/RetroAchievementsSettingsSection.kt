package com.omnidroid.app.mobile.feature.settings.general

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.omnidroid.BuildConfig
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.dialog.DialogActionRole
import com.omnidroid.app.mobile.shared.compose.dialog.DialogWidth
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogButton
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogTextField
import com.omnidroid.app.mobile.shared.compose.ui.chromeMutedContentColor
import com.omnidroid.app.shared.ra.RaModuleUpdateWork
import com.omnidroid.app.shared.ra.RetroAchievementsCoordinator
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsList
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsMenuLink
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsSwitch
import com.omnidroid.app.utils.android.settings.SettingsHorizontalDivider
import com.omnidroid.app.utils.android.settings.booleanPreferenceState
import com.omnidroid.app.utils.android.settings.indexPreferenceState
import com.omnidroid.lib.ra.RaUpdateCheckOutcome
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface RetroAchievementsSettingsEntryPoint {
    fun retroAchievementsCoordinator(): RetroAchievementsCoordinator
}

@Composable
internal fun RetroAchievementsSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator =
        remember(context) {
            EntryPointAccessors
                .fromApplication(
                    context.applicationContext,
                    RetroAchievementsSettingsEntryPoint::class.java,
                ).retroAchievementsCoordinator()
        }

    val raEnabledState = booleanPreferenceState(R.string.pref_key_ra_enabled, false)
    val hardcoreState = booleanPreferenceState(R.string.pref_key_ra_hardcore, true)
    val encoreState = booleanPreferenceState(R.string.pref_key_ra_encore, false)
    val unofficialState = booleanPreferenceState(R.string.pref_key_ra_unofficial, false)
    val spectatorState = booleanPreferenceState(R.string.pref_key_ra_spectator, false)
    val notificationsState = booleanPreferenceState(R.string.pref_key_ra_notifications, true)
    val leaderboardsState = booleanPreferenceState(R.string.pref_key_ra_leaderboards, true)
    val richPresenceState = booleanPreferenceState(R.string.pref_key_ra_rich_presence, true)
    val soundsState = booleanPreferenceState(R.string.pref_key_ra_sounds, true)

    val posValues = stringArrayResource(R.array.pref_key_ra_notification_position_values).toList()
    val posNames = stringArrayResource(R.array.pref_key_ra_notification_position_display_names).toList()
    val positionIndexState = indexPreferenceState(R.string.pref_key_ra_notification_position, "top_left", posValues)

    var signedInUser by remember { mutableStateOf(coordinator.savedUsername()) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var moduleStatus by remember { mutableStateOf(coordinator.deliveryStatus()) }
    var checkingModule by remember { mutableStateOf(false) }

    if (showLoginDialog) {
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var isLoggingIn by remember { mutableStateOf(false) }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        OmnidroidDialog(
            width = DialogWidth.Input,
            onDismissRequest = { if (!isLoggingIn) showLoginDialog = false },
            dismissOnBackPress = !isLoggingIn,
            dismissOnClickOutside = !isLoggingIn,
            title = { Text(stringResource(R.string.ra_login_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.ra_password_not_stored_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = chromeMutedContentColor,
                    )
                    OmnidroidDialogTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            errorMsg = null
                        },
                        enabled = !isLoggingIn,
                        label = { Text(stringResource(R.string.ra_username_label)) },
                    )
                    OmnidroidDialogTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMsg = null
                        },
                        enabled = !isLoggingIn,
                        label = { Text(stringResource(R.string.ra_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    if (isLoggingIn) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    }
                    errorMsg?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                OmnidroidDialogButton(
                    enabled = !isLoggingIn && username.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        isLoggingIn = true
                        errorMsg = null
                        scope.launch {
                            val result = coordinator.loginWithPassword(username, password)
                            isLoggingIn = false
                            result
                                .onSuccess { user ->
                                    signedInUser = user.username
                                    raEnabledState.value = true
                                    moduleStatus = coordinator.deliveryStatus()
                                    showLoginDialog = false
                                }.onFailure { err ->
                                    errorMsg = context.getString(R.string.ra_login_failed, err.message ?: "Error")
                                }
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (isLoggingIn) R.string.ra_logging_in else R.string.ra_login_title,
                        ),
                    )
                }
            },
            dismissButton = {
                OmnidroidDialogButton(
                    role = DialogActionRole.Cancel,
                    enabled = !isLoggingIn,
                    onClick = { showLoginDialog = false },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showLogoutDialog) {
        val currentUser = signedInUser.orEmpty()
        OmnidroidDialog(
            onDismissRequest = { showLogoutDialog = false },
            destructive = true,
            title = { Text(stringResource(R.string.ra_logout_action)) },
            text = { Text(stringResource(R.string.ra_logout_confirm_message, currentUser)) },
            confirmButton = {
                OmnidroidDialogButton(
                    role = DialogActionRole.Destructive,
                    onClick = {
                        coordinator.logout()
                        signedInUser = null
                        showLogoutDialog = false
                    },
                ) {
                    Text(stringResource(R.string.ra_logout_action))
                }
            },
            dismissButton = {
                OmnidroidDialogButton(
                    role = DialogActionRole.Cancel,
                    onClick = { showLogoutDialog = false },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    OmnidroidSettingsGroup(titleRes = R.string.ra_settings_section_account) {
        OmnidroidSettingsSwitch(
            state = raEnabledState,
            titleRes = R.string.ra_enable_title,
            subtitleRes = R.string.ra_enable_description,
            onCheckedChange = { enabled ->
                if (enabled) {
                    RaModuleUpdateWork.schedule(context.applicationContext)
                }
            },
        )
        if (signedInUser != null) {
            OmnidroidSettingsMenuLink(
                title = { Text(stringResource(R.string.ra_signed_in_as, signedInUser.orEmpty())) },
                subtitleRes = R.string.ra_logout_action,
                onClick = { showLogoutDialog = true },
            )
        } else {
            OmnidroidSettingsMenuLink(
                titleRes = R.string.ra_login_title,
                subtitleRes = R.string.ra_login_subtitle,
                onClick = { showLoginDialog = true },
            )
        }
    }

    SettingsHorizontalDivider()

    OmnidroidSettingsGroup(titleRes = R.string.ra_settings_section_gameplay) {
        OmnidroidSettingsSwitch(
            state = hardcoreState,
            titleRes = R.string.ra_hardcore_title,
            subtitleRes = R.string.ra_hardcore_description,
        )
        OmnidroidSettingsSwitch(
            state = encoreState,
            titleRes = R.string.ra_encore_title,
            subtitleRes = R.string.ra_encore_description,
        )
        OmnidroidSettingsSwitch(
            state = unofficialState,
            titleRes = R.string.ra_unofficial_title,
            subtitleRes = R.string.ra_unofficial_description,
        )
        OmnidroidSettingsSwitch(
            state = spectatorState,
            titleRes = R.string.ra_spectator_title,
            subtitleRes = R.string.ra_spectator_description,
        )
    }

    SettingsHorizontalDivider()

    OmnidroidSettingsGroup(titleRes = R.string.ra_settings_section_overlays) {
        OmnidroidSettingsSwitch(
            state = notificationsState,
            titleRes = R.string.ra_notifications_title,
            subtitleRes = R.string.ra_notifications_description,
        )
        OmnidroidSettingsList(
            state = positionIndexState,
            items = posNames,
            titleRes = R.string.ra_notification_position_title,
        )
        OmnidroidSettingsSwitch(
            state = leaderboardsState,
            titleRes = R.string.ra_leaderboards_title,
            subtitleRes = R.string.ra_leaderboards_description,
        )
        OmnidroidSettingsSwitch(
            state = richPresenceState,
            titleRes = R.string.ra_rich_presence_title,
            subtitleRes = R.string.ra_rich_presence_description,
        )
        OmnidroidSettingsSwitch(
            state = soundsState,
            titleRes = R.string.ra_sounds_title,
            subtitleRes = R.string.ra_sounds_description,
        )
    }

    SettingsHorizontalDivider()

    OmnidroidSettingsGroup(titleRes = R.string.ra_settings_section_module) {
        val loadable = remember(moduleStatus) { coordinator.moduleManager.resolveLoadableModule() }
        val versionLine =
            when {
                loadable?.isBundledFallback == true -> stringResource(R.string.ra_module_bundled)
                !moduleStatus.installedVersion.isNullOrBlank() ->
                    stringResource(
                        R.string.ra_module_version_summary,
                        moduleStatus.installedVersion.orEmpty(),
                        moduleStatus.rcheevosVersion?.ifBlank { "12.5.0" } ?: "12.5.0",
                    )
                else -> stringResource(R.string.ra_module_not_installed)
            }
        val lastCheckedText =
            if (moduleStatus.lastCheckEpochMs > 0L) {
                DateFormat
                    .getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(moduleStatus.lastCheckEpochMs))
            } else {
                stringResource(R.string.ra_module_never_checked)
            }

        val statusSummary =
            buildString {
                append(versionLine)
                append('\n')
                append(context.getString(R.string.ra_module_last_checked, lastCheckedText))
                moduleStatus.lastError?.takeIf { it.isNotBlank() }?.let { err ->
                    append('\n')
                    append(context.getString(R.string.ra_module_last_error, err))
                }
            }

        OmnidroidSettingsMenuLink(
            titleRes = R.string.ra_module_status_title,
            subtitle = {
                Column {
                    Text(statusSummary)
                    if (!BuildConfig.GITHUB_UPDATES) {
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = stringResource(R.string.ra_module_play_channel_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = chromeMutedContentColor,
                        )
                    }
                }
            },
            onClick = {},
        )

        OmnidroidSettingsMenuLink(
            titleRes = R.string.ra_module_check_updates,
            enabled = !checkingModule,
            subtitle = {
                Text(
                    if (checkingModule) {
                        stringResource(R.string.ra_module_checking)
                    } else if (!BuildConfig.GITHUB_UPDATES) {
                        stringResource(R.string.ra_module_play_channel_note)
                    } else {
                        stringResource(R.string.ra_module_last_checked, lastCheckedText)
                    },
                )
            },
            onClick = {
                checkingModule = true
                scope.launch {
                    val outcome = coordinator.checkForModuleUpdates(force = true)
                    moduleStatus = coordinator.deliveryStatus()
                    checkingModule = false
                    val toastMessage =
                        when (outcome) {
                            is RaUpdateCheckOutcome.Updated -> {
                                context.getString(R.string.ra_module_updated, outcome.module.version)
                            }
                            is RaUpdateCheckOutcome.UpToDate -> {
                                context.getString(R.string.ra_module_up_to_date, outcome.version)
                            }
                            is RaUpdateCheckOutcome.Failed -> {
                                context.getString(R.string.ra_module_update_failed, outcome.message)
                            }
                            RaUpdateCheckOutcome.DisabledOnDistributionChannel -> {
                                context.getString(R.string.ra_module_play_channel_note)
                            }
                        }
                    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}
