package com.omnidroid.app.mobile.feature.settings.general

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.omnidroid.R
import com.omnidroid.app.update.AppUpdater
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsGroup
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsMenuLink
import com.omnidroid.app.utils.android.settings.SettingsHorizontalDivider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@Composable
internal fun AboutSettings() {
    val context = LocalContext.current
    var showLicenses by remember { mutableStateOf(false) }
    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) {
                    Text(text = stringResource(id = R.string.settings_action_close))
                }
            },
            title = { Text(text = stringResource(R.string.settings_title_open_source_licenses)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text = stringResource(id = R.string.settings_license_soundtouch))
                }
            },
        )
    }

    UpdateSettings()
    SettingsHorizontalDivider()

    OmnidroidSettingsGroup(
        titleRes = R.string.settings_section_privacy,
    ) {
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_title_privacy_policy,
            subtitleRes = R.string.settings_description_privacy_policy,
            onClick = { openExternalUrl(context, context.getString(R.string.url_privacy_policy)) },
        )
    }
    SettingsHorizontalDivider()

    OmnidroidSettingsGroup(
        titleRes = R.string.settings_section_donations,
    ) {
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_title_donate_buy_me_a_coffee,
            subtitleRes = R.string.settings_description_donate_buy_me_a_coffee,
            onClick = {
                openExternalUrl(context, context.getString(R.string.url_donate_buy_me_a_coffee))
            },
        )
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_title_donate_ko_fi,
            subtitleRes = R.string.settings_description_donate_ko_fi,
            onClick = { openExternalUrl(context, context.getString(R.string.url_donate_ko_fi)) },
        )
    }
    SettingsHorizontalDivider()

    OmnidroidSettingsGroup(
        titleRes = R.string.settings_title_open_source_licenses,
    ) {
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_title_open_source_licenses,
            subtitleRes = R.string.settings_description_open_source_licenses,
            onClick = { showLicenses = true },
        )
    }
    SettingsHorizontalDivider()

    OmnidroidSettingsGroup(
        titleRes = R.string.settings_section_about_developer,
    ) {
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_title_about_developer,
            subtitleRes = R.string.settings_description_about_developer,
            onClick = { openExternalUrl(context, context.getString(R.string.url_developer_github)) },
        )
    }
}

@Composable
private fun UpdateSettings() {
    val appUpdater = rememberAppUpdater()
    val scope = rememberCoroutineScope()

    OmnidroidSettingsGroup(
        titleRes = R.string.settings_section_updates,
    ) {
        OmnidroidSettingsMenuLink(
            titleRes = R.string.settings_title_check_for_updates,
            subtitleRes = R.string.settings_description_check_for_updates,
            onClick = {
                scope.launch {
                    runCatching { appUpdater.checkForUpdate(force = true) }
                }
            },
        )
    }
}

@Composable
private fun rememberAppUpdater(): AppUpdater {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppUpdaterEntryPoint::class.java,
        ).appUpdater()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppUpdaterEntryPoint {
    fun appUpdater(): AppUpdater
}

private fun openExternalUrl(
    context: Context,
    url: String,
) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
