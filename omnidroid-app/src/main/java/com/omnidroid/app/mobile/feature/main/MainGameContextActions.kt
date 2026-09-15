package com.omnidroid.app.mobile.feature.main

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.HomeChromeBackground
import com.omnidroid.app.mobile.shared.compose.ui.OmnidroidGameTexts
import com.omnidroid.app.mobile.shared.compose.ui.OmnidroidSmallGameImage
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.savesync.GameCloudSyncOverride

private val GameSettingsOverlayWidth = 380.dp
private val PanelMutedWhite = Color.White.copy(alpha = 0.72f)
private val PanelDivider = Color.White.copy(alpha = 0.08f)

@Composable
fun MainGameContextActions(
    selectedGameState: MutableState<Game?>,
    retrogradeDb: RetrogradeDatabase,
    shortcutSupported: Boolean,
    saveSyncSupported: Boolean = false,
    cloudOverride: GameCloudSyncOverride? = null,
    frameSpeed: Int = 1,
    onGamePlay: (Game) -> Unit,
    onGameRestart: (Game) -> Unit,
    onFavoriteToggle: (Game, Boolean) -> Unit,
    onCreateShortcut: (Game) -> Unit,
    onCloudOverride: (Game, GameCloudSyncOverride) -> Unit = { _, _ -> },
    onFrameSpeed: (Game, Int) -> Unit = { _, _ -> },
    onSyncGameNow: (Game) -> Unit = {},
    onSetCustomThumbnail: (Game, Uri) -> Unit = { _, _ -> },
    onRemoveCustomThumbnail: (Game) -> Unit = {},
    onSetCustomName: (Game, String) -> Unit = { _, _ -> },
    onRemoveCustomName: (Game) -> Unit = {},
) {
    val selectedGame = selectedGameState.value
    val observedGame by retrogradeDb.gameDao().observeById(selectedGame?.id ?: -1)
        .collectAsState(initial = selectedGame)
    var lastGame by remember { mutableStateOf<Game?>(null) }
    val latestGame = observedGame ?: selectedGame
    LaunchedEffect(latestGame) {
        if (latestGame != null) lastGame = latestGame
    }
    val panelGame = if (selectedGame != null) latestGame ?: selectedGame else lastGame
    val visible = selectedGame != null

    var pendingCoverGame by remember { mutableStateOf<Game?>(null) }
    val coverPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val game = pendingCoverGame
            pendingCoverGame = null
            if (uri != null && game != null) {
                onSetCustomThumbnail(game, uri)
            }
        }
    var showNameDialog by remember { mutableStateOf(false) }
    var nameDraft by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().zIndex(24f)) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.58f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { selectedGameState.value = null },
                    )
        }
        AnimatedVisibility(
            visible = visible && panelGame != null,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            panelGame?.let { game ->
                GameSettingsPanel(
                    game = game,
                    shortcutSupported = shortcutSupported,
                    saveSyncSupported = saveSyncSupported,
                    initialCloudOverride = cloudOverride ?: GameCloudSyncOverride.INHERIT,
                    initialFrameSpeed = frameSpeed,
                    onDismiss = { selectedGameState.value = null },
                    onGamePlay = onGamePlay,
                    onGameRestart = onGameRestart,
                    onFavoriteToggle = onFavoriteToggle,
                    onCreateShortcut = onCreateShortcut,
                    onCloudOverride = onCloudOverride,
                    onFrameSpeed = onFrameSpeed,
                    onSyncGameNow = onSyncGameNow,
                    onSetCustomThumbnail = {
                        pendingCoverGame = game
                        coverPicker.launch("image/*")
                    },
                    onRemoveCustomThumbnail = { onRemoveCustomThumbnail(game) },
                    onSetCustomName = {
                        nameDraft = game.customName ?: game.title
                        showNameDialog = true
                    },
                    onRemoveCustomName = { onRemoveCustomName(game) },
                )
            }
        }
    }

    if (showNameDialog && panelGame != null) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(text = stringResource(R.string.game_context_menu_set_custom_name)) },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.game_custom_name_hint)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetCustomName(panelGame, nameDraft)
                        showNameDialog = false
                    },
                ) {
                    Text(text = stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun GameSettingsPanel(
    game: Game,
    shortcutSupported: Boolean,
    saveSyncSupported: Boolean,
    initialCloudOverride: GameCloudSyncOverride,
    initialFrameSpeed: Int,
    onDismiss: () -> Unit,
    onGamePlay: (Game) -> Unit,
    onGameRestart: (Game) -> Unit,
    onFavoriteToggle: (Game, Boolean) -> Unit,
    onCreateShortcut: (Game) -> Unit,
    onCloudOverride: (Game, GameCloudSyncOverride) -> Unit,
    onFrameSpeed: (Game, Int) -> Unit,
    onSyncGameNow: (Game) -> Unit,
    onSetCustomThumbnail: () -> Unit,
    onRemoveCustomThumbnail: () -> Unit,
    onSetCustomName: () -> Unit,
    onRemoveCustomName: () -> Unit,
) {
    val fastForwardSupported =
        remember(game.systemId) {
            runCatching { GameSystem.findById(game.systemId).fastForwardSupport }
                .getOrDefault(false)
        }
    var cloudOverride by remember(game.id) { mutableStateOf(initialCloudOverride) }
    var frameSpeed by remember(game.id) { mutableIntStateOf(initialFrameSpeed) }
    val hasCustomCover = CoverUtils.hasCustomCover(game)
    val hasCustomName = !game.customName.isNullOrBlank()
    val speedLabels = stringArrayResource(R.array.game_menu_fast_forward_speeds)
    val speedValues =
        stringArrayResource(R.array.game_menu_fast_forward_speed_values)
            .map { it.toInt() }

    Surface(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(GameSettingsOverlayWidth),
        shape = RectangleShape,
        color = HomeChromeBackground,
        contentColor = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier =
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(LibraryNeonGreen),
                )
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .windowInsetsPadding(
                                WindowInsets.safeContent.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom,
                                ),
                            ),
                ) {
                    GameSettingsHeader(onClose = onDismiss)
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(PanelDivider),
                    )
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 16.dp),
                    ) {
                        ContextActionHeader(game = game)
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .height(1.dp)
                                    .background(PanelDivider),
                        )
                        ContextActionEntry(
                            label = stringResource(id = R.string.game_context_menu_resume),
                            icon = Icons.Default.PlayArrow,
                            onClick = {
                                onGamePlay(game)
                                onDismiss()
                            },
                        )
                        ContextActionEntry(
                            label = stringResource(id = R.string.game_context_menu_restart),
                            icon = Icons.Default.RestartAlt,
                            onClick = {
                                onGameRestart(game)
                                onDismiss()
                            },
                        )
                        if (game.isFavorite) {
                            ContextActionEntry(
                                label = stringResource(id = R.string.game_context_menu_remove_from_favorites),
                                icon = Icons.Default.FavoriteBorder,
                                onClick = { onFavoriteToggle(game, false) },
                            )
                        } else {
                            ContextActionEntry(
                                label = stringResource(id = R.string.game_context_menu_add_to_favorites),
                                icon = Icons.Default.Favorite,
                                onClick = { onFavoriteToggle(game, true) },
                            )
                        }
                        if (shortcutSupported) {
                            ContextActionEntry(
                                label = stringResource(id = R.string.game_context_menu_create_shortcut),
                                icon = Icons.Default.AppShortcut,
                                onClick = { onCreateShortcut(game) },
                            )
                        }

                        if (fastForwardSupported) {
                            SectionLabel(text = stringResource(R.string.game_menu_fast_forward))
                            speedLabels.forEachIndexed { index, label ->
                                ChoiceRow(
                                    selected = frameSpeed == speedValues[index],
                                    label = label,
                                    onClick = {
                                        frameSpeed = speedValues[index]
                                        onFrameSpeed(game, speedValues[index])
                                    },
                                )
                            }
                            Text(
                                text = stringResource(R.string.game_menu_fast_forward_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = PanelMutedWhite,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            )
                        }

                        if (saveSyncSupported) {
                            SectionLabel(text = stringResource(R.string.game_cloud_save))
                            ChoiceRow(
                                selected = cloudOverride == GameCloudSyncOverride.INHERIT,
                                label = stringResource(R.string.game_cloud_save_inherit),
                                onClick = {
                                    cloudOverride = GameCloudSyncOverride.INHERIT
                                    onCloudOverride(game, GameCloudSyncOverride.INHERIT)
                                },
                            )
                            ChoiceRow(
                                selected = cloudOverride == GameCloudSyncOverride.ALWAYS,
                                label = stringResource(R.string.game_cloud_save_always),
                                onClick = {
                                    cloudOverride = GameCloudSyncOverride.ALWAYS
                                    onCloudOverride(game, GameCloudSyncOverride.ALWAYS)
                                },
                            )
                            ChoiceRow(
                                selected = cloudOverride == GameCloudSyncOverride.NEVER,
                                label = stringResource(R.string.game_cloud_save_never),
                                onClick = {
                                    cloudOverride = GameCloudSyncOverride.NEVER
                                    onCloudOverride(game, GameCloudSyncOverride.NEVER)
                                },
                            )
                            ContextActionEntry(
                                label = stringResource(id = R.string.game_cloud_save_sync_now),
                                icon = Icons.Outlined.CloudSync,
                                onClick = { onSyncGameNow(game) },
                            )
                        }

                        SectionLabel(text = stringResource(R.string.game_context_menu_set_custom_thumbnail))
                        ContextActionEntry(
                            label =
                                stringResource(
                                    if (hasCustomCover) {
                                        R.string.game_context_menu_change_thumbnail
                                    } else {
                                        R.string.game_context_menu_set_custom_thumbnail
                                    },
                                ),
                            icon = Icons.Outlined.Image,
                            onClick = onSetCustomThumbnail,
                        )
                        if (hasCustomCover) {
                            ContextActionEntry(
                                label = stringResource(id = R.string.game_context_menu_remove_thumbnail),
                                icon = Icons.Outlined.HideImage,
                                onClick = onRemoveCustomThumbnail,
                            )
                        }

                        SectionLabel(text = stringResource(R.string.game_context_menu_set_custom_name))
                        ContextActionEntry(
                            label =
                                stringResource(
                                    if (hasCustomName) {
                                        R.string.game_context_menu_change_name
                                    } else {
                                        R.string.game_context_menu_set_custom_name
                                    },
                                ),
                            icon = Icons.Outlined.Edit,
                            onClick = onSetCustomName,
                        )
                        if (hasCustomName) {
                            ContextActionEntry(
                                label = stringResource(id = R.string.game_context_menu_remove_name),
                                icon = Icons.Default.Close,
                                onClick = onRemoveCustomName,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameSettingsHeader(onClose: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onClose,
            modifier =
                Modifier
                    .size(40.dp)
                    .controllerFocusGlow(CircleShape),
            shape = CircleShape,
            color = Color.Transparent,
            contentColor = Color.White,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.close),
                )
            }
        }
        Text(
            text = stringResource(R.string.game_settings),
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            color = Color.White,
            modifier = Modifier.padding(start = 8.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = PanelMutedWhite,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ContextActionHeader(game: Game) {
    Row(
        modifier =
            Modifier.padding(
                start = 16.dp,
                top = 8.dp,
                bottom = 8.dp,
                end = 16.dp,
            ),
    ) {
        OmnidroidSmallGameImage(
            modifier =
                Modifier
                    .width(40.dp)
                    .height(40.dp)
                    .align(Alignment.CenterVertically),
            game = game,
        )
        OmnidroidGameTexts(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            game = game,
        )
    }
}

@Composable
private fun ContextActionEntry(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .controllerFocusGlow(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.padding(start = 16.dp),
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
        )
        Text(
            modifier = Modifier.padding(start = 16.dp),
            text = label,
            color = Color.White,
        )
    }
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .controllerFocusGlow(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors =
                RadioButtonDefaults.colors(
                    selectedColor = LibraryNeonGreen,
                    unselectedColor = PanelMutedWhite,
                ),
        )
        Text(text = label, color = Color.White)
    }
}
