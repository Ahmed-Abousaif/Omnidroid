package com.swordfish.lemuroid.app.mobile.feature.main

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.app.mobile.shared.controller.controllerFocusGlow
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.HomeChromeBackground
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.SystemStatusIndicators
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncWork

@Composable
fun MainTopBar(
    currentRoute: MainRoute,
    navController: NavHostController,
    onHelpPressed: () -> Unit,
    mainUIState: MainViewModel.UiState,
) {
    Column {
        LemuroidTopAppBar(
            route = currentRoute,
            navController = navController,
            mainUIState = mainUIState,
            onHelpPressed = onHelpPressed,
        )

        AnimatedVisibility(mainUIState.operationInProgress) {
            LinearProgressIndicator(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp),
            )
        }
    }
}

@Composable
fun LemuroidTopAppBar(
    route: MainRoute,
    navController: NavController,
    mainUIState: MainViewModel.UiState,
    onHelpPressed: () -> Unit,
) {
    val context = LocalContext.current

    Surface(color = HomeChromeBackground, tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = route.parent != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                CompactBarIconButton(
                    onClick = { navController.popBackStack() },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.back),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = stringResource(route.titleId),
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LemuroidTopBarActions(
                route = route,
                navController = navController,
                context = context,
                saveSyncEnabled = mainUIState.saveSyncEnabled,
                onHelpPressed = onHelpPressed,
                operationsInProgress = mainUIState.operationInProgress,
            )
            SystemStatusIndicators()
        }
    }
}

@Composable
fun LemuroidTopBarActions(
    route: MainRoute,
    navController: NavController,
    context: Context,
    saveSyncEnabled: Boolean,
    operationsInProgress: Boolean,
    onHelpPressed: () -> Unit,
) {
    Row {
        CompactBarIconButton(
            onClick = onHelpPressed,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = stringResource(R.string.mobile_settings_help),
                modifier = Modifier.size(18.dp),
            )
        }
        if (saveSyncEnabled) {
            CompactBarIconButton(
                onClick = { SaveSyncWork.enqueueManualWork(context.applicationContext) },
                enabled = !operationsInProgress,
            ) {
                Icon(
                    Icons.Outlined.CloudSync,
                    contentDescription = stringResource(R.string.save_sync),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (route.showBottomNavigation) {
            CompactBarIconButton(
                onClick = { navController.navigate(MainRoute.SETTINGS.route) },
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.settings),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun CompactBarIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp).controllerFocusGlow(),
    ) {
        content()
    }
}
