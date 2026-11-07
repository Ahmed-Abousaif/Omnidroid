package com.omnidroid.app.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.homeChromeCardColor

@Composable
fun AppUpdateOverlay(
    state: UpdateState,
    supportsGitHubOptions: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
    onRestart: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is UpdateState.Available -> {
            var showDetails by remember(state.version) { mutableStateOf(true) }
            if (showDetails) {
                AlertDialog(
                    onDismissRequest = {
                        showDetails = false
                        onLater()
                    },
                    title = {
                        Text(
                            text =
                                stringResource(
                                    R.string.app_update_available_title,
                                    state.version,
                                ),
                        )
                    },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            if (state.notes.isNotBlank()) {
                                Text(text = state.notes, style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Text(text = stringResource(R.string.app_update_available_body))
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = onUpdate) {
                            Text(text = stringResource(R.string.app_update_action_update))
                        }
                    },
                    dismissButton = {
                        Row {
                            if (supportsGitHubOptions) {
                                TextButton(onClick = onSkip) {
                                    Text(text = stringResource(R.string.app_update_action_skip))
                                }
                            }
                            TextButton(
                                onClick = {
                                    showDetails = false
                                    onLater()
                                },
                            ) {
                                Text(text = stringResource(R.string.app_update_action_later))
                            }
                        }
                    },
                )
            }
        }
        is UpdateState.Downloading -> {
            Column(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .background(homeChromeCardColor, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_update_downloading),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        is UpdateState.ReadyToInstall -> {
            Column(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .background(homeChromeCardColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = stringResource(R.string.app_update_ready_title),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onRestart) {
                    Text(text = stringResource(R.string.app_update_action_restart))
                }
            }
        }
        is UpdateState.Error -> {
            AlertDialog(
                onDismissRequest = onDismissError,
                confirmButton = {
                    TextButton(onClick = onDismissError) {
                        Text(text = stringResource(R.string.settings_action_close))
                    }
                },
                title = { Text(text = stringResource(R.string.app_update_error_title)) },
                text = {
                    Text(
                        text =
                            state.message?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.app_update_error_generic),
                    )
                },
            )
        }
        is UpdateState.UpToDate -> {
            AlertDialog(
                onDismissRequest = onLater,
                confirmButton = {
                    TextButton(onClick = onLater) {
                        Text(text = stringResource(R.string.settings_action_close))
                    }
                },
                title = { Text(text = stringResource(R.string.app_update_up_to_date_title)) },
                text = { Text(text = stringResource(R.string.app_update_up_to_date_body)) },
            )
        }
        else -> Unit
    }
}
