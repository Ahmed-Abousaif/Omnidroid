package com.omnidroid.app.mobile.feature.main

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.zIndex
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.addconsole.AddConsoleScreen
import com.omnidroid.app.mobile.feature.addconsole.AddConsoleViewModel
import com.omnidroid.app.mobile.feature.cast.CastPickerSheet
import com.omnidroid.app.mobile.feature.gamedetails.GameDetailsScreen
import com.omnidroid.app.mobile.feature.gamedetails.GameDetailsViewModel
import com.omnidroid.app.mobile.feature.favorites.FavoritesScreen
import com.omnidroid.app.mobile.feature.favorites.FavoritesViewModel
import com.omnidroid.app.mobile.feature.games.GamesScreen
import com.omnidroid.app.mobile.feature.games.GamesViewModel
import com.omnidroid.app.mobile.feature.library.LibraryFilter
import com.omnidroid.app.mobile.feature.library.LibraryScreen
import com.omnidroid.app.mobile.feature.library.LibraryTopBar
import com.omnidroid.app.mobile.feature.library.LibraryTopBarRowHeight
import com.omnidroid.app.mobile.feature.library.LibraryViewModel
import com.omnidroid.app.mobile.feature.library.RegisteredSystemsStore
import com.omnidroid.app.mobile.feature.search.SearchScreen
import com.omnidroid.app.mobile.feature.search.SearchViewModel
import com.omnidroid.app.mobile.feature.settings.advanced.AdvancedSettingsScreen
import com.omnidroid.app.mobile.feature.settings.advanced.AdvancedSettingsViewModel
import com.omnidroid.app.mobile.feature.settings.bios.BiosScreen
import com.omnidroid.app.mobile.feature.settings.bios.BiosSettingsViewModel
import com.omnidroid.app.mobile.feature.settings.coreselection.CoresSelectionScreen
import com.omnidroid.app.mobile.feature.settings.coreselection.CoresSelectionViewModel
import com.omnidroid.app.mobile.feature.settings.general.SettingsScreen
import com.omnidroid.app.mobile.feature.settings.general.SettingsViewModel
import com.omnidroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsScreen
import com.omnidroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsViewModel
import com.omnidroid.app.mobile.feature.settings.savesync.SaveSyncSettingsScreen
import com.omnidroid.app.mobile.feature.settings.savesync.SaveSyncSettingsViewModel
import com.omnidroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.omnidroid.app.mobile.feature.systems.MetaSystemsScreen
import com.omnidroid.app.mobile.feature.systems.MetaSystemsViewModel
import com.omnidroid.app.mobile.shared.compose.ui.AppTheme
import com.omnidroid.app.mobile.shared.compose.ui.GameHeroCover
import com.omnidroid.app.mobile.shared.compose.ui.HomeChromeBackground
import com.omnidroid.app.mobile.shared.controller.ControllerHintBar
import com.omnidroid.app.mobile.shared.controller.ControllerHints
import com.omnidroid.app.mobile.shared.controller.ControllerInputBridge
import com.omnidroid.app.mobile.shared.controller.ControllerNavigationState
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.app.shared.input.omnidroiddevice.OmnidroidInputDeviceGamePad
import com.omnidroid.app.shared.input.omnidroiddevice.getOmnidroidInputDevice
import com.omnidroid.app.shared.GameInteractor
import com.omnidroid.app.shared.cast.CastDisplayManager
import com.omnidroid.app.shared.game.BaseGameActivity
import com.omnidroid.app.shared.game.GameLauncher
import com.omnidroid.app.shared.input.InputDeviceManager
import com.omnidroid.app.shared.main.BusyActivity
import com.omnidroid.app.shared.main.GameLaunchTaskHandler
import com.omnidroid.app.shared.settings.SettingsInteractor
import com.omnidroid.app.tv.channel.ChannelUpdateWork
import com.omnidroid.app.tv.shared.TVHelper
import com.omnidroid.common.coroutines.safeLaunch
import com.omnidroid.ext.feature.review.ReviewManager
import com.omnidroid.lib.android.RetrogradeComponentActivity
import com.omnidroid.lib.bios.BiosManager
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.core.CoresSelection
import com.omnidroid.lib.library.MetaSystemID
import com.omnidroid.lib.library.SystemID
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import com.omnidroid.lib.savesync.SaveSyncManager
import com.omnidroid.lib.storage.DirectoriesManager
import dagger.hilt.android.AndroidEntryPoint
import de.charlex.compose.material3.HtmlText
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(DelicateCoroutinesApi::class)
class MainActivity : RetrogradeComponentActivity(), BusyActivity {
    @Inject
    lateinit var gameLaunchTaskHandler: GameLaunchTaskHandler

    @Inject
    lateinit var saveSyncManager: SaveSyncManager

    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var gameInteractor: GameInteractor

    @Inject
    lateinit var biosManager: BiosManager

    @Inject
    lateinit var coresSelection: CoresSelection

    @Inject
    lateinit var settingsInteractor: SettingsInteractor

    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var coreUpdater: CoreUpdater

    @Inject
    lateinit var castDisplayManager: CastDisplayManager

    private val reviewManager = ReviewManager()
    private val controllerBridge = ControllerInputBridge()
    private val homePressEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var notificationPermissionCallback: ((Boolean) -> Unit)? = null
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationPermissionCallback?.invoke(granted)
            notificationPermissionCallback = null
        }

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext, saveSyncManager)
    }

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        hideSystemBars()
        ensureLegacyStoragePermissionsIfNeeded()

        GlobalScope.safeLaunch {
            reviewManager.initialize(applicationContext)
        }

        setContent {
            val navController = rememberNavController()
            MainScreen(navController)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen(navController: NavHostController) {
        AppTheme {
            val controllerNav = remember { ControllerNavigationState() }
            val focusManager = LocalFocusManager.current
            val navBackStackEntry = navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry.value?.destination
            val currentRoute =
                currentDestination?.route
                    ?.let { MainRoute.findByRoute(it) }
                    ?: MainRoute.HOME

            val infoDialogDisplayed =
                remember {
                    mutableStateOf(false)
                }
            val showCastPicker =
                remember {
                    mutableStateOf(false)
                }
            val castState = castDisplayManager.state.collectAsState().value

            LaunchedEffect(currentRoute) {
                mainViewModel.changeRoute(currentRoute)
            }

            val selectedGameState =
                remember {
                    mutableStateOf<Game?>(null)
                }
            val scope = rememberCoroutineScope()
            val heroProgress = remember { Animatable(0f) }
            val heroGame = remember { mutableStateOf<Game?>(null) }
            val heroFrom = remember { mutableStateOf(Rect.Zero) }
            val heroTo = remember { mutableStateOf(Rect.Zero) }
            val coverBounds = remember { mutableMapOf<Int, Rect>() }
            val waitingForDest = remember { mutableStateOf(false) }
            val closingDetails = remember { mutableStateOf(false) }

            val onGameLongClick = { game: Game ->
                selectedGameState.value = game
            }

            val openGameDetails = { game: Game ->
                if (currentRoute == MainRoute.HOME) {
                    val from = coverBounds[game.id]
                    if (from != null && from.width > 8f) {
                        heroGame.value = game
                        heroFrom.value = from
                        heroTo.value = Rect.Zero
                        waitingForDest.value = true
                        closingDetails.value = false
                        scope.launch { heroProgress.snapTo(0f) }
                    }
                }
                navController.navigateToGameDetails(game.id)
            }

            val closeGameDetails = {
                if (currentRoute == MainRoute.GAME_DETAILS && !closingDetails.value) {
                    closingDetails.value = true
                    waitingForDest.value = false
                    scope.launch {
                        if (heroGame.value != null) {
                            heroProgress.animateTo(
                                0f,
                                tween(durationMillis = 480, easing = FastOutSlowInEasing),
                            )
                        }
                        navController.popBackStack()
                        heroGame.value = null
                        heroTo.value = Rect.Zero
                        closingDetails.value = false
                    }
                }
            }

            val onGameClick = { game: Game ->
                openGameDetails(game)
            }

            val onGameFavoriteToggle = { game: Game, isFavorite: Boolean ->
                gameInteractor.onFavoriteToggle(game, isFavorite)
            }

            val onHelpPressed = {
                infoDialogDisplayed.value = true
            }

            val mainUIState =
                mainViewModel.state
                    .collectAsState(MainViewModel.UiState())
                    .value

            val registeredSystemsStore =
                remember {
                    RegisteredSystemsStore(applicationContext)
                }
            val libraryViewModel =
                viewModel<LibraryViewModel>(
                    factory =
                        LibraryViewModel.Factory(
                            applicationContext,
                            retrogradeDb,
                            registeredSystemsStore,
                        ),
                )
            val libraryState = libraryViewModel.state.collectAsState().value
            val gamepadConnected =
                inputDeviceManager.getGamePadsObservable()
                    .collectAsState(emptyList())
                    .value
                    .any { it.getOmnidroidInputDevice() is OmnidroidInputDeviceGamePad }

            val libraryHints =
                ControllerHints.library(
                    switchConsoles = stringResource(R.string.controller_hint_switch_consoles),
                    zoom = stringResource(R.string.controller_hint_zoom),
                    select = stringResource(R.string.controller_hint_select),
                    back = stringResource(R.string.controller_hint_back),
                    options = stringResource(R.string.controller_hint_options),
                    search = stringResource(R.string.controller_hint_search),
                )
            val standardHints =
                ControllerHints.standard(
                    select = stringResource(R.string.controller_hint_select),
                    back = stringResource(R.string.controller_hint_back),
                    options = stringResource(R.string.controller_hint_options),
                )
            val simpleHints =
                ControllerHints.standard(
                    select = stringResource(R.string.controller_hint_select),
                    back = stringResource(R.string.controller_hint_back),
                )
            val addConsoleHints =
                ControllerHints.carousel(
                    switchConsoles = stringResource(R.string.controller_hint_switch_consoles),
                    select = stringResource(R.string.controller_hint_install),
                    back = stringResource(R.string.controller_hint_back),
                )
            val settingsHints =
                ControllerHints.carousel(
                    switchConsoles = stringResource(R.string.controller_hint_switch_categories),
                    select = stringResource(R.string.controller_hint_select),
                    back = stringResource(R.string.controller_hint_back),
                )

            LaunchedEffect(currentRoute, selectedGameState.value) {
                controllerNav.setHints(
                    when {
                        selectedGameState.value != null -> simpleHints
                        currentRoute == MainRoute.HOME -> libraryHints
                        currentRoute == MainRoute.GAME_DETAILS -> standardHints
                        currentRoute == MainRoute.ADD_CONSOLES -> addConsoleHints
                        currentRoute == MainRoute.SETTINGS -> settingsHints
                        else -> simpleHints
                    },
                )
            }

            controllerNav.onBack = {
                if (controllerNav.textInputActive || controllerNav.searchArmed.value) {
                    controllerNav.dismissSearch()
                } else if (selectedGameState.value != null) {
                    selectedGameState.value = null
                } else if (currentRoute == MainRoute.GAME_DETAILS) {
                    closeGameDetails()
                } else {
                    handleLauncherBack(
                        navController = navController,
                        currentRoute = currentRoute,
                        libraryState = libraryState,
                        infoDialogDisplayed = infoDialogDisplayed,
                        selectedGameState = selectedGameState,
                        showCastPicker = showCastPicker,
                        libraryViewModel = libraryViewModel,
                    )
                }
            }
            controllerNav.onOptions = {
                val focusedGame = controllerNav.focusedGame.value
                when {
                    selectedGameState.value != null -> selectedGameState.value = null
                    focusedGame != null -> selectedGameState.value = focusedGame
                    currentRoute != MainRoute.SETTINGS -> navController.navigateToRoute(MainRoute.SETTINGS)
                }
            }
            controllerNav.onSearch = {
                if (currentRoute != MainRoute.HOME) {
                    navigateToLibraryHome(navController)
                }
                controllerNav.searchArmed.value = true
            }
            controllerNav.onScan = {
                if (currentRoute == MainRoute.HOME && !libraryState.operationInProgress) {
                    libraryViewModel.syncLibrary(this@MainActivity)
                }
            }
            controllerNav.onZoomIn = {
                if (currentRoute == MainRoute.HOME) {
                    libraryViewModel.zoomIn()
                }
            }
            controllerNav.onZoomOut = {
                if (currentRoute == MainRoute.HOME) {
                    libraryViewModel.zoomOut()
                }
            }
            controllerNav.onPrevConsole = {
                when (currentRoute) {
                    MainRoute.HOME -> libraryViewModel.cycleFilter(-1)
                    MainRoute.ADD_CONSOLES -> controllerNav.onCarouselPrev()
                    MainRoute.SETTINGS -> controllerNav.onCycleSettings?.invoke(-1)
                    else -> { }
                }
            }
            controllerNav.onNextConsole = {
                when (currentRoute) {
                    MainRoute.HOME -> libraryViewModel.cycleFilter(1)
                    MainRoute.ADD_CONSOLES -> controllerNav.onCarouselNext()
                    MainRoute.SETTINGS -> controllerNav.onCycleSettings?.invoke(1)
                    else -> { }
                }
            }
            SideEffect {
                controllerBridge.navigation = controllerNav
            }

            LaunchedEffect(Unit) {
                homePressEvents.collect {
                    selectedGameState.value = null
                    infoDialogDisplayed.value = false
                    heroGame.value = null
                    closingDetails.value = false
                    waitingForDest.value = false
                    heroProgress.snapTo(0f)
                    libraryViewModel.resetHomeUi()
                    navigateToLibraryHome(navController)
                }
            }

            BackHandler {
                if (selectedGameState.value != null) {
                    selectedGameState.value = null
                } else if (currentRoute == MainRoute.GAME_DETAILS) {
                    closeGameDetails()
                } else {
                    handleLauncherBack(
                        navController = navController,
                        currentRoute = currentRoute,
                        libraryState = libraryState,
                        infoDialogDisplayed = infoDialogDisplayed,
                        selectedGameState = selectedGameState,
                        showCastPicker = showCastPicker,
                        libraryViewModel = libraryViewModel,
                    )
                }
            }

            val libraryChromeRoute =
                currentRoute == MainRoute.HOME ||
                    currentRoute == MainRoute.ADD_CONSOLES ||
                    currentRoute == MainRoute.SETTINGS ||
                    currentRoute == MainRoute.SETTINGS_ADVANCED ||
                    currentRoute == MainRoute.SETTINGS_BIOS ||
                    currentRoute == MainRoute.SETTINGS_CORES_SELECTION ||
                    currentRoute == MainRoute.SETTINGS_INPUT_DEVICES ||
                    currentRoute == MainRoute.SETTINGS_SAVE_SYNC

            CompositionLocalProvider(LocalControllerNavigation provides controllerNav) {
            Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = HomeChromeBackground,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (gamepadConnected) {
                        ControllerHintBar(
                            visible = true,
                            hints = controllerNav.hints.value,
                        )
                    }
                },
                topBar = {
                    if (!libraryChromeRoute && currentRoute != MainRoute.GAME_DETAILS) {
                        MainTopBar(
                            currentRoute = currentRoute,
                            navController = navController,
                            onHelpPressed = onHelpPressed,
                            mainUIState = mainUIState,
                        )
                    }
                },
            ) { padding ->
                val heroActive = heroGame.value != null
                val showLibraryUnderlay =
                    currentRoute == MainRoute.HOME || currentRoute == MainRoute.GAME_DETAILS
                Box(modifier = Modifier.fillMaxSize()) {
                    if (showLibraryUnderlay) {
                        LibraryScreen(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .padding(top = LibraryTopBarRowHeight)
                                    .zIndex(if (currentRoute == MainRoute.HOME) 1f else 0f),
                            viewModel = libraryViewModel,
                            onGameClick = onGameClick,
                            onContinueClick = {
                                libraryViewModel.clearSearchQuery()
                                gameInteractor.onGamePlay(it)
                            },
                            onGameLongClick = onGameLongClick,
                            onAddConsole = { navController.navigateToRoute(MainRoute.ADD_CONSOLES) },
                            controllerConnected = gamepadConnected,
                            transitionProgress = if (heroActive) heroProgress.value else 0f,
                            transitioningGameId = heroGame.value?.id,
                            onCoverBounds = { id, rect -> coverBounds[id] = rect },
                        )
                    }
                NavHost(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .zIndex(if (currentRoute == MainRoute.HOME) 0f else 1f)
                            .then(
                                if (libraryChromeRoute) {
                                    Modifier.padding(top = LibraryTopBarRowHeight)
                                } else {
                                    Modifier
                                },
                            ),
                    navController = navController,
                    startDestination = MainRoute.HOME.route,
                ) {
                    composable(MainRoute.HOME, instant = true) {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                    composable(MainRoute.GAME_DETAILS, instant = true) { entry ->
                        val gameId = entry.arguments?.getInt("gameId") ?: return@composable
                        GameDetailsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        GameDetailsViewModel.Factory(
                                            applicationContext,
                                            retrogradeDb,
                                            gameId,
                                        ),
                                ),
                            onBack = {
                                if (heroGame.value != null) {
                                    closeGameDetails()
                                } else {
                                    navController.popBackStack()
                                }
                            },
                            onPlay = {
                                libraryViewModel.clearSearchQuery()
                                gameInteractor.onGamePlay(it)
                            },
                            onFavoriteToggle = onGameFavoriteToggle,
                            onOpenSettings = { selectedGameState.value = it },
                            transitionProgress = if (heroActive) heroProgress.value else 1f,
                            hideCover = heroActive && (heroProgress.value < 0.97f || closingDetails.value),
                            onCoverBounds = { rect ->
                                heroTo.value = rect
                                if (waitingForDest.value && rect.width > 8f && !closingDetails.value) {
                                    waitingForDest.value = false
                                    scope.launch {
                                        heroProgress.animateTo(
                                            1f,
                                            tween(durationMillis = 520, easing = FastOutSlowInEasing),
                                        )
                                    }
                                }
                            },
                        )
                    }
                    composable(MainRoute.ADD_CONSOLES) {
                        AddConsoleScreen(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        AddConsoleViewModel.Factory(
                                            applicationContext,
                                            retrogradeDb,
                                            registeredSystemsStore,
                                            coreUpdater,
                                            coresSelection,
                                        ),
                                ),
                        )
                    }
                    composable(MainRoute.FAVORITES) {
                        FavoritesScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory = FavoritesViewModel.Factory(retrogradeDb),
                                ),
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                        )
                    }
                    composable(MainRoute.SEARCH) {
                        SearchScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory = SearchViewModel.Factory(retrogradeDb),
                                ),
                            searchQuery = mainUIState.searchQuery,
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                            onGameFavoriteToggle = onGameFavoriteToggle,
                            onResetSearchQuery = { mainViewModel.changeQueryString("") },
                        )
                    }
                    composable(MainRoute.SYSTEMS) {
                        MetaSystemsScreen(
                            modifier = Modifier.padding(padding),
                            navController = navController,
                            viewModel =
                                viewModel(
                                    factory =
                                        MetaSystemsViewModel.Factory(
                                            retrogradeDb,
                                            applicationContext,
                                        ),
                                ),
                        )
                    }
                    composable(MainRoute.SYSTEM_GAMES) { entry ->
                        val metaSystemId = entry.arguments?.getString("metaSystemId")
                        GamesScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        GamesViewModel.Factory(
                                            retrogradeDb,
                                            MetaSystemID.valueOf(metaSystemId!!),
                                        ),
                                ),
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                            onGameFavoriteToggle = onGameFavoriteToggle,
                        )
                    }
                    composable(MainRoute.SETTINGS) {
                        SettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        SettingsViewModel.Factory(
                                            applicationContext,
                                            settingsInteractor,
                                            saveSyncManager,
                                            FlowSharedPreferences(
                                                SharedPreferencesHelper.getLegacySharedPreferences(
                                                    applicationContext,
                                                ),
                                            ),
                                        ),
                                ),
                            navController = navController,
                        )
                    }
                    composable(MainRoute.SETTINGS_ADVANCED) {
                        AdvancedSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        AdvancedSettingsViewModel.Factory(
                                            applicationContext,
                                            settingsInteractor,
                                        ),
                                ),
                            navController = navController,
                        )
                    }
                    composable(MainRoute.SETTINGS_BIOS) {
                        BiosScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory = BiosSettingsViewModel.Factory(biosManager),
                                ),
                        )
                    }
                    composable(MainRoute.SETTINGS_CORES_SELECTION) {
                        CoresSelectionScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        CoresSelectionViewModel.Factory(
                                            applicationContext,
                                            coresSelection,
                                        ),
                                ),
                        )
                    }
                    composable(MainRoute.SETTINGS_INPUT_DEVICES) {
                        InputDevicesSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        InputDevicesSettingsViewModel.Factory(
                                            applicationContext,
                                            inputDeviceManager,
                                        ),
                                ),
                        )
                    }
                    composable(MainRoute.SETTINGS_SAVE_SYNC) {
                        SaveSyncSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        SaveSyncSettingsViewModel.Factory(
                                            application,
                                            saveSyncManager,
                                        ),
                                ),
                        )
                    }
                }
                }
            }

            MainGameContextActions(
                selectedGameState = selectedGameState,
                retrogradeDb = retrogradeDb,
                shortcutSupported = gameInteractor.supportShortcuts(),
                saveSyncSupported = gameInteractor.isSaveSyncSupported(),
                cloudOverride = selectedGameState.value?.let { gameInteractor.getCloudOverride(it) },
                frameSpeed = selectedGameState.value?.let { gameInteractor.getFrameSpeed(it) } ?: 1,
                onGamePlay = {
                    libraryViewModel.clearSearchQuery()
                    gameInteractor.onGamePlay(it)
                },
                onGameRestart = {
                    libraryViewModel.clearSearchQuery()
                    gameInteractor.onGameRestart(it)
                },
                onFavoriteToggle = { game: Game, isFavorite: Boolean ->
                    gameInteractor.onFavoriteToggle(game, isFavorite)
                },
                onCreateShortcut = { gameInteractor.onCreateShortcut(it) },
                onCloudOverride = { game, override -> gameInteractor.setCloudOverride(game, override) },
                onFrameSpeed = { game, speed -> gameInteractor.setFrameSpeed(game, speed) },
                onSyncGameNow = { gameInteractor.syncGameNow(it) },
                onSetCustomThumbnail = { game, uri -> gameInteractor.onSetCustomCover(game, uri) },
                onRemoveCustomThumbnail = { gameInteractor.onRemoveCustomCover(it) },
                onSetCustomName = { game, name -> gameInteractor.onSetCustomName(game, name) },
                onRemoveCustomName = { gameInteractor.onRemoveCustomName(it) },
            )

            if (showCastPicker.value) {
                CastPickerSheet(
                    state = castState,
                    onSelectDisplay = { displayId ->
                        castDisplayManager.selectDisplay(displayId)
                        showCastPicker.value = false
                        castDisplayManager.showIdle(this@MainActivity)
                    },
                    onStopCasting = {
                        castDisplayManager.stopCasting()
                        showCastPicker.value = false
                    },
                    onFindDisplay = { castDisplayManager.openSystemCastSettings(this@MainActivity) },
                    onDismiss = { showCastPicker.value = false },
                )
            }

            if (infoDialogDisplayed.value) {
                val message =
                    remember {
                        val systemFolders =
                            SystemID.values()
                                .joinToString(", ") { "<i>${it.dbname}</i>" }

                        getString(R.string.omnidroid_help_content)
                            .replace("\$SYSTEMS", systemFolders)
                    }

                AlertDialog(
                    text = { HtmlText(text = message) },
                    onDismissRequest = { infoDialogDisplayed.value = false },
                    confirmButton = { },
                )
            }

            if (libraryChromeRoute || currentRoute == MainRoute.GAME_DETAILS || heroGame.value != null) {
                val compactProgress =
                    when {
                        currentRoute == MainRoute.GAME_DETAILS && heroGame.value != null ->
                            heroProgress.value
                        currentRoute == MainRoute.GAME_DETAILS -> 1f
                        else -> 0f
                    }
                LibraryTopBar(
                    modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
                    operationInProgress =
                        libraryState.operationInProgress || mainUIState.operationInProgress,
                    gamepadConnected = gamepadConnected,
                    onScanPressed = { libraryViewModel.syncLibrary(this@MainActivity) },
                    onCastPressed = {
                        castDisplayManager.refresh()
                        showCastPicker.value = true
                    },
                    onSettingsPressed = { navController.navigateToRoute(MainRoute.SETTINGS) },
                    overlayTitleId =
                        currentRoute.takeIf {
                            it != MainRoute.HOME && it != MainRoute.GAME_DETAILS
                        }?.titleId,
                    onBackPressed = {
                        if (currentRoute == MainRoute.GAME_DETAILS) {
                            closeGameDetails()
                        } else {
                            navController.popBackStack()
                        }
                    },
                    compactProgress = compactProgress,
                    searchQuery = libraryState.searchQuery,
                    onSearchQueryChange = { libraryViewModel.changeQueryString(it) },
                    onClearSearch = { libraryViewModel.clearSearchQuery() },
                    casting = castState.isCasting,
                    consoleTitleId =
                        (libraryState.filter as? LibraryFilter.System)?.metaSystemID?.titleResId,
                    consoleGameCount = libraryState.selectedSystemGameCount,
                )
            }
            val hero = heroGame.value
            if (hero != null && (heroProgress.value < 0.97f || closingDetails.value)) {
                GameHeroCover(
                    game = hero,
                    from = heroFrom.value,
                    to = heroTo.value,
                    progress = heroProgress.value,
                )
            }
            }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return controllerBridge.dispatchKey(event) { super.dispatchKeyEvent(it) }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (controllerBridge.dispatchMotion(event) { super.dispatchKeyEvent(it) }) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        castDisplayManager.refresh()
        castDisplayManager.showIdle(this)
    }

    override fun onDestroy() {
        castDisplayManager.hideIdle()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            homePressEvents.tryEmit(Unit)
        }
    }

    private fun navigateToLibraryHome(navController: NavHostController) {
        val returnedHome = navController.popBackStack(MainRoute.HOME.route, inclusive = false)
        if (!returnedHome) {
            navController.navigate(MainRoute.HOME.route) {
                launchSingleTop = true
            }
        }
    }

    private fun handleLauncherBack(
        navController: NavHostController,
        currentRoute: MainRoute,
        libraryState: LibraryViewModel.UiState,
        infoDialogDisplayed: MutableState<Boolean>,
        selectedGameState: MutableState<Game?>,
        showCastPicker: MutableState<Boolean>,
        libraryViewModel: LibraryViewModel,
    ) {
        when {
            showCastPicker.value -> {
                showCastPicker.value = false
            }
            infoDialogDisplayed.value -> {
                infoDialogDisplayed.value = false
            }
            selectedGameState.value != null -> {
                selectedGameState.value = null
            }
            currentRoute != MainRoute.HOME -> {
                navigateToLibraryHome(navController)
            }
            libraryState.searchQuery.isNotEmpty() -> {
                libraryViewModel.clearSearchQuery()
            }
            libraryState.filter !is LibraryFilter.All -> {
                libraryViewModel.selectFilter(LibraryFilter.All)
            }
        }
    }

    fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
        notificationPermissionCallback = onResult
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun ensureLegacyStoragePermissionsIfNeeded() {
        if (TVHelper.isSAFSupported(this) || hasLegacyStoragePermission()) {
            return
        }
        storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasLegacyStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun activity(): Activity = this

    override fun isBusy(): Boolean = mainViewModel.state.value.operationInProgress ?: false

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            BaseGameActivity.REQUEST_PLAY_GAME -> {
                castDisplayManager.restoreIdle(this)
                GlobalScope.safeLaunch {
                    gameLaunchTaskHandler.handleGameFinish(
                        true,
                        this@MainActivity,
                        resultCode,
                        data,
                    )
                    if (TVHelper.isTV(applicationContext)) {
                        ChannelUpdateWork.enqueue(applicationContext)
                    }
                }
            }
        }
    }
}
