package com.omnidroid.app.mobile.feature.library.gamemenu

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.omnidroid.R
import com.omnidroid.app.OmnidroidApplication
import com.omnidroid.app.mobile.feature.library.LibraryGameSubtext
import com.omnidroid.app.mobile.feature.main.CoverSearchDialog
import com.omnidroid.app.mobile.shared.compose.dialog.DialogWidth
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogButton
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogTextField
import com.omnidroid.app.mobile.shared.compose.gamemenu.CloudSaveMenuChoice
import com.omnidroid.app.mobile.shared.compose.gamemenu.FrameSpeedMenuChoice
import com.omnidroid.app.mobile.shared.compose.gamemenu.MenuDrawableIcon
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuAction
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuLink
import com.omnidroid.app.mobile.shared.compose.sidemenu.MenuSection
import com.omnidroid.app.mobile.shared.compose.sidemenu.SideMenuDestination
import com.omnidroid.app.mobile.shared.compose.sidemenu.SideMenuNavHost
import com.omnidroid.app.mobile.shared.compose.sidemenu.SideMenuPage
import com.omnidroid.app.mobile.shared.compose.sidemenu.SideMenuScaffold
import com.omnidroid.app.mobile.shared.compose.sidemenu.openMenuPage
import com.omnidroid.app.mobile.shared.compose.sidemenu.rememberSideMenuState
import com.omnidroid.app.mobile.shared.compose.sidemenu.validateSideMenuDestinations
import com.omnidroid.app.mobile.shared.compose.ui.LibraryNeonGreen
import com.omnidroid.app.mobile.shared.compose.ui.OmnidroidSmallGameImage
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.app.mobile.shared.controller.ControllerHintBar
import com.omnidroid.app.mobile.shared.controller.ControllerHintBarRowHeight
import com.omnidroid.app.mobile.shared.controller.ControllerHints
import com.omnidroid.app.mobile.shared.controller.ControllerNavigationState
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.app.shared.covers.CoverUtils
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.savesync.GameCloudSyncPreferences
import com.omnidroid.lib.savesync.GameFrameSpeedPreferences
import com.omnidroid.metadata.libretrodb.CoverSearcher
import kotlinx.coroutines.launch

private val rootPage = SideMenuDestination("game", R.string.game_settings)
private val appearancePage = SideMenuDestination("appearance", R.string.game_menu_appearance, rootPage.route)
private val libraryPages = listOf(rootPage, appearancePage)

@Composable
fun LibraryGameMenu(
    selectedGameState: MutableState<Game?>,
    retrogradeDb: RetrogradeDatabase,
    saveSyncSupported: Boolean,
    gamepadConnected: Boolean,
    coverSearcher: CoverSearcher?,
    actions: LibraryGameMenuActions,
) {
    var retained by remember { mutableStateOf<Game?>(null) }
    var pendingCoverId by rememberSaveable { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val latestActions by rememberUpdatedState(actions)
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val gameId = pendingCoverId
        pendingCoverId = null
        if (uri != null && gameId != null) {
            OmnidroidApplication.scope(context.applicationContext).launch {
                retrogradeDb.gameDao().selectById(gameId)?.let { latestActions.setThumbnail(it, uri) }
            }
        }
    }
    val selected = selectedGameState.value
    val sessionGame = selected ?: retained ?: return
    SideEffect { if (selected != null) retained = selected }
    key(sessionGame.id) {
        remember { validateSideMenuDestinations(libraryPages) }
        val menu = rememberSideMenuState()
        val navController = rememberNavController()
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route ?: rootPage.route
        val page = libraryPages.firstOrNull { it.route == currentRoute } ?: rootPage
        val query = remember(sessionGame.id, retrogradeDb) { retrogradeDb.gameDao().observeById(sessionGame.id) }
        val observed by query.collectAsState(initial = sessionGame)
        val game = observed?.takeIf { it.id == sessionGame.id } ?: sessionGame
        var nameDraft by remember { mutableStateOf<String?>(null) }
        var searchCover by remember { mutableStateOf(false) }
        val parentController = LocalControllerNavigation.current
        val controller = remember { ControllerNavigationState() }
        val hints = ControllerHints.standard(stringResource(R.string.controller_hint_select), stringResource(R.string.controller_hint_back))
        DisposableEffect(parentController, controller) {
            parentController?.modalNavigation = controller
            onDispose {
                if (parentController?.modalNavigation == controller) {
                    parentController.modalNavigation = null
                    runCatching { parentController.contentFocusRequester?.requestFocus() }
                }
            }
        }
        SideEffect {
            controller.setHints(hints)
            controller.onBack = {
                when {
                    nameDraft != null -> nameDraft = null
                    searchCover -> searchCover = false
                    currentRoute != rootPage.route -> navController.popBackStack()
                    else -> menu.closeThen()
                }
            }
            controller.onOptions = { menu.closeThen() }
        }
        LaunchedEffect(selected == null) { if (selected == null) menu.closeThen() }
        CompositionLocalProvider(LocalControllerNavigation provides controller) {
            Box(Modifier.fillMaxSize().zIndex(24f)) {
                SideMenuScaffold(
                    state = menu, title = stringResource(page.titleRes), canGoBack = page.parent != null,
                    onBack = { navController.popBackStack() },
                    bottomReserved = if (gamepadConnected) ControllerHintBarRowHeight else 0.dp,
                    onClosed = {
                        if (selectedGameState.value?.id == sessionGame.id) selectedGameState.value = null
                        retained = null
                    },
                ) {
                    SideMenuNavHost(navController, rootPage.route) {
                        composable(rootPage.route) {
                            LibraryGameMenuRoot(game, saveSyncSupported, latestActions,
                                onPlay = { menu.closeThen { latestActions.play(game) } },
                                onRestart = { menu.closeThen { latestActions.restart(game) } },
                                onNavigate = { navController.openMenuPage(it) },
                            )
                        }
                        composable(appearancePage.route) {
                            GameAppearancePage(
                                game, latestActions, coverSearcher != null,
                                onSearch = { searchCover = true },
                                onPick = { pendingCoverId = game.id; coverPicker.launch("image/*") },
                                onName = { nameDraft = game.customName ?: game.title },
                            )
                        }
                    }
                }
                if (gamepadConnected) ControllerHintBar(
                    visible = menu.transition.targetState, hints = controller.hints.value,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                )
            }
            if (searchCover && coverSearcher != null) {
                CoverSearchDialog(
                    game, coverSearcher, onDismiss = { searchCover = false },
                    onUpdateCoverOnly = { latestActions.setCoverUrl(game, it) },
                    onFixFullMatch = { title, url -> latestActions.fixMatch(game, title, url) },
                )
            }
            nameDraft?.let { draft ->
                OmnidroidDialog(
                    width = DialogWidth.Input,
                    onDismissRequest = { nameDraft = null },
                    title = { Text(stringResource(R.string.game_context_menu_set_custom_name)) },
                    text = { OmnidroidDialogTextField(value = draft, onValueChange = { nameDraft = it }, singleLine = true,
                        label = { Text(stringResource(R.string.game_custom_name_hint)) }) },
                    confirmButton = { OmnidroidDialogButton(onClick = { latestActions.setName(game, draft); nameDraft = null }) { Text(stringResource(R.string.ok)) } },
                    dismissButton = { OmnidroidDialogButton(onClick = { nameDraft = null }) { Text(stringResource(R.string.cancel)) } },
                )
            }
        }
    }
}

@Composable
private fun LibraryGameMenuRoot(
    game: Game, syncSupported: Boolean, actions: LibraryGameMenuActions,
    onPlay: () -> Unit, onRestart: () -> Unit, onNavigate: (SideMenuDestination) -> Unit,
) {
    val context = LocalContext.current
    val speeds = remember(context) { GameFrameSpeedPreferences(context) }
    val cloud = remember(context) { GameCloudSyncPreferences(context) }
    val speedChanges = remember(speeds, game.id) { speeds.observe(game.id) }
    val cloudChanges = remember(cloud, game.id) { cloud.observeOverride(game.id) }
    val speed by speedChanges.collectAsState(speeds.get(game.id))
    val override by cloudChanges.collectAsState(cloud.getOverride(game.id))
    val speedSupported = remember(game.systemId) { runCatching { GameSystem.findById(game.systemId).fastForwardSupport }.getOrDefault(false) }
    SideMenuPage("library-game") {
        MenuSection("identity") { ContextActionHeader(game) }
        MenuSection("commands") {
            MenuAction("resume", titleRes = R.string.game_context_menu_resume,
                icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }, onClick = onPlay)
            MenuAction("restart", titleRes = R.string.game_context_menu_restart,
                icon = { MenuDrawableIcon(R.drawable.ic_menu_restart) }, onClick = onRestart)
            MenuAction("favorite", titleRes = if (game.isFavorite) R.string.game_context_menu_remove_from_favorites else R.string.game_context_menu_add_to_favorites,
                icon = { Icon(if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null, tint = if (game.isFavorite) LibraryNeonGreen else LocalContentColor.current) },
            ) { actions.favorite(game, !game.isFavorite) }
        }
        MenuSection("playback", visible = speedSupported || syncSupported) {
            if (speedSupported) FrameSpeedMenuChoice(speed) { actions.frameSpeed(game, it) }
            if (syncSupported) {
                CloudSaveMenuChoice(override) { actions.cloudOverride(game, it) }
                MenuAction("sync-now", titleRes = R.string.game_cloud_save_sync_now,
                    icon = { Icon(Icons.Outlined.CloudSync, contentDescription = null) },
                ) { actions.syncNow(game) }
            }
        }
        MenuSection("appearance") { MenuLink("appearance", appearancePage, onNavigate) }
    }
}

@Composable
private fun GameAppearancePage(
    game: Game, actions: LibraryGameMenuActions, canSearch: Boolean,
    onSearch: () -> Unit, onPick: () -> Unit, onName: () -> Unit,
) {
    val customCover = CoverUtils.hasCustomCover(game)
    val customName = !game.customName.isNullOrBlank()
    SideMenuPage("game-appearance") {
        MenuSection("appearance-actions") {
            if (canSearch) MenuAction("search-cover", titleRes = R.string.game_context_menu_search_cover,
                icon = { Icon(Icons.Default.Search, contentDescription = null) }, onClick = onSearch)
            MenuAction("pick-cover", titleRes = if (customCover) R.string.game_context_menu_change_thumbnail else R.string.game_context_menu_set_custom_thumbnail,
                icon = { Icon(Icons.Outlined.Image, contentDescription = null) }, onClick = onPick)
            if (customCover) MenuAction("remove-cover", titleRes = R.string.game_context_menu_remove_thumbnail,
                icon = { Icon(Icons.Outlined.HideImage, contentDescription = null) },
            ) { actions.removeThumbnail(game) }
            MenuAction("edit-name", titleRes = if (customName) R.string.game_context_menu_change_name else R.string.game_context_menu_set_custom_name,
                subtitle = if (customName) ({ Text(game.customName.orEmpty()) }) else null,
                icon = { Icon(Icons.Outlined.Edit, contentDescription = null) }, onClick = onName)
            if (customName) MenuAction("remove-name", titleRes = R.string.game_context_menu_remove_name,
                icon = { Icon(Icons.Default.Close, contentDescription = null) },
            ) { actions.removeName(game) }
        }
    }
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
                    .width(56.dp)
                    .align(Alignment.CenterVertically)
                    .clip(RoundedCornerShape(4.dp)),
            game = game,
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
                    .padding(start = 12.dp),
        ) {
            Text(
                text = game.displayName,
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = chromeColors.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            LibraryGameSubtext(
                game = game,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
