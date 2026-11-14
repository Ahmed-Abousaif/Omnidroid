package com.omnidroid.app.mobile.feature.main

import androidx.compose.material3.Text

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
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
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.omnidroid.BuildConfig
import com.omnidroid.R
import com.omnidroid.app.OmnidroidApplication
import com.omnidroid.app.mobile.feature.addconsole.AddConsoleScreen
import com.omnidroid.app.mobile.feature.addconsole.AddConsoleViewModel
import com.omnidroid.app.mobile.feature.cast.CastPickerSheet
import com.omnidroid.app.mobile.feature.gamedetails.GameDetailsScreen
import com.omnidroid.app.mobile.feature.gamedetails.GameDetailsViewModel
import com.omnidroid.app.mobile.feature.library.LibraryFilter
import com.omnidroid.app.mobile.feature.library.LibraryLayout
import com.omnidroid.app.mobile.feature.library.LibraryScreen
import com.omnidroid.app.mobile.feature.library.LibraryStartupStore
import com.omnidroid.app.mobile.feature.library.LibraryTopBar
import com.omnidroid.app.mobile.feature.library.LibraryViewModel
import com.omnidroid.app.mobile.feature.library.RegisteredSystemsStore
import com.omnidroid.app.mobile.feature.library.gamemenu.LibraryGameMenu
import com.omnidroid.app.mobile.feature.library.gamemenu.LibraryGameMenuActions
import com.omnidroid.app.mobile.feature.profile.ProfileScreen
import com.omnidroid.app.mobile.feature.profile.ProfileViewModel
import com.omnidroid.app.mobile.feature.settings.deviceprofile.ExtendedBenchmarkProgressDialog
import com.omnidroid.app.mobile.feature.settings.deviceprofile.WelcomeBenchmarkDialog
import com.omnidroid.app.mobile.feature.settings.general.SettingsScreen
import com.omnidroid.app.mobile.feature.settings.general.SettingsSubscreen
import com.omnidroid.app.mobile.feature.settings.general.SettingsSubscreenContent
import com.omnidroid.app.mobile.feature.settings.general.SettingsViewModel
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.dialog.DialogWidth
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogButton
import com.omnidroid.app.mobile.shared.compose.ui.AppTheme
import com.omnidroid.app.mobile.shared.compose.ui.GameCoverOrigin
import com.omnidroid.app.mobile.shared.compose.ui.LocalAdaptiveLayout
import com.omnidroid.app.mobile.shared.compose.ui.calculateAdaptiveLayoutSpec
import com.omnidroid.app.mobile.shared.compose.ui.homeChromeColor
import com.omnidroid.app.mobile.shared.controller.ControllerHintBar
import com.omnidroid.app.mobile.shared.controller.ControllerHints
import com.omnidroid.app.mobile.shared.controller.ControllerInputBridge
import com.omnidroid.app.mobile.shared.controller.ControllerNavigationState
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.app.shared.GameInteractor
import com.omnidroid.app.shared.cast.CastDisplayManager
import com.omnidroid.app.shared.cast.StopCastResult
import com.omnidroid.app.shared.deviceprofile.DeviceProfileManager
import com.omnidroid.app.shared.game.BaseGameActivity
import com.omnidroid.app.shared.game.GamePlatformSession
import com.omnidroid.app.shared.input.InputDeviceManager
import com.omnidroid.app.shared.input.omnidroiddevice.OmnidroidInputDeviceGamePad
import com.omnidroid.app.shared.input.omnidroiddevice.getOmnidroidInputDevice
import com.omnidroid.app.shared.main.BusyActivity
import com.omnidroid.app.shared.main.GameLaunchTaskHandler
import com.omnidroid.app.shared.settings.SettingsInteractor
import com.omnidroid.app.tv.channel.ChannelUpdateWork
import com.omnidroid.app.tv.shared.TVHelper
import com.omnidroid.app.update.AppUpdateOverlay
import com.omnidroid.app.update.AppUpdater
import com.omnidroid.common.coroutines.safeLaunch
import com.omnidroid.ext.feature.review.ReviewManager
import com.omnidroid.lib.android.RetrogradeComponentActivity
import com.omnidroid.lib.bios.BiosManager
import com.omnidroid.lib.core.CoreUpdater
import com.omnidroid.lib.core.CoresSelection
import com.omnidroid.lib.core.MetadataSliceInstaller
import com.omnidroid.lib.library.SystemID
import com.omnidroid.lib.library.MetaSystemID
import android.content.Context
import com.omnidroid.lib.library.db.RetrogradeDatabase
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import com.omnidroid.lib.savesync.SaveSyncManager
import com.omnidroid.lib.storage.DirectoriesManager
import dagger.hilt.android.AndroidEntryPoint
import de.charlex.compose.material3.HtmlText
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit

@AndroidEntryPoint
class MainActivity : RetrogradeComponentActivity(), BusyActivity {
    private var consoleRequest by mutableStateOf<Pair<MetaSystemID, Int>?>(null)
    private var consoleRequestSequence = 0

    companion object {
        private const val EXTRA_CONSOLE = "console_setup_meta_system"

        fun consoleIntent(context: Context, console: MetaSystemID): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_CONSOLE, console.name)
    }

    private fun readConsoleRequest(intent: Intent) {
        val console = intent.getStringExtra(EXTRA_CONSOLE)?.let {
            runCatching { MetaSystemID.valueOf(it) }.getOrNull()
        } ?: return
        consoleRequest = console to ++consoleRequestSequence
        intent.removeExtra(EXTRA_CONSOLE)
    }
    @Inject
    lateinit var gameLaunchTaskHandler: GameLaunchTaskHandler

    @Inject
    lateinit var saveSyncManager: SaveSyncManager

    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var libretroDb: com.omnidroid.metadata.libretrodb.db.LibretroDBManager

    @Inject
    lateinit var coverSearcher: com.omnidroid.metadata.libretrodb.CoverSearcher

    @Inject
    lateinit var sliceInstaller: MetadataSliceInstaller

    @Inject
    lateinit var settingsManager: com.omnidroid.app.mobile.feature.settings.SettingsManager

    @Inject
    lateinit var gameInteractor: GameInteractor

    @Inject
    lateinit var biosManager: BiosManager

    @Inject
    lateinit var directoriesManager: DirectoriesManager

    @Inject
    lateinit var retrofit: Retrofit

    @Inject
    lateinit var sharedPreferences: android.content.SharedPreferences

    @Inject
    lateinit var coresSelection: CoresSelection

    @Inject
    lateinit var settingsInteractor: SettingsInteractor

    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var coreUpdater: CoreUpdater

    @Inject
    lateinit var coreVariablesManager: com.omnidroid.lib.core.CoreVariablesManager

    @Inject
    lateinit var castDisplayManager: CastDisplayManager

    @Inject
    lateinit var appUpdater: AppUpdater

    private val reviewManager = ReviewManager()
    private val controllerBridge = ControllerInputBridge()
    private var notificationPermissionCallback: ((Boolean) -> Unit)? = null
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationPermissionCallback?.invoke(granted)
            notificationPermissionCallback = null
        }

    private val appUpdateLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext)
    }

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        readConsoleRequest(intent)
        hideSystemBars()
        ensureLegacyStoragePermissionsIfNeeded()

        OmnidroidApplication.scope(this).safeLaunch {
            reviewManager.initialize(applicationContext)
        }

        OmnidroidApplication.scope(this).safeLaunch {
            runCatching { appUpdater.checkForUpdate(force = false) }
        }

        setContent {
            val navController = rememberNavController()
            MainScreen(navController)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
    @Composable
    private fun MainScreen(navController: NavHostController) {
        AppTheme {
            LaunchedEffect(consoleRequest) {
                if (consoleRequest != null) {
                    navController.navigate(MainRoute.ADD_CONSOLES.route) { launchSingleTop = true }
                }
            }
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

            val deviceProfileManager = remember { DeviceProfileManager.get(applicationContext) }
            val showWelcomeBenchmark = remember { mutableStateOf(false) }
            val showExtendedBenchmark = remember { mutableStateOf(false) }
            val extendedBenchmarkProgress = remember { mutableStateOf(0f) }

            LaunchedEffect(Unit) {
                if (!deviceProfileManager.isInitialCaptureDone()) {
                    launch(Dispatchers.Default) {
                        deviceProfileManager.ensureInitialCapture()
                    }
                }
                if (!deviceProfileManager.isBenchmarkPromptShown()) {
                    showWelcomeBenchmark.value = true
                }
            }

            val selectedGameState =
                remember {
                    mutableStateOf<Game?>(null)
                }
            val scope = rememberCoroutineScope()

            var lastClickedGame by remember { mutableStateOf<Game?>(null) }

            val onGameLongClick = { game: Game ->
                selectedGameState.value = game
            }

            val openGameDetails = { game: Game ->
                lastClickedGame = game
                navController.navigateToGameDetails(game.id)
            }

            val closeGameDetails = {
                navController.popBackStack()
            }

            val onGameClick = { game: Game ->
                openGameDetails(game)
            }

            val onGameFavoriteToggle = { game: Game, isFavorite: Boolean ->
                gameInteractor.onFavoriteToggle(game, isFavorite)
            }

            // Keep the lazily started pending-operation monitor active for isBusy().
            mainViewModel.state.collectAsState()

            val registeredSystemsStore =
                remember {
                    RegisteredSystemsStore(applicationContext)
                }
            val startupStore =
                remember {
                    LibraryStartupStore(applicationContext)
                }
            val libraryViewModel =
                viewModel<LibraryViewModel>(
                    factory =
                        LibraryViewModel.Factory(
                            applicationContext,
                            retrogradeDb,
                            registeredSystemsStore,
                            startupStore,
                        ),
                )
            val libraryState = libraryViewModel.state.collectAsState().value
            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    libraryViewModel.autoSyncLibrary(this@MainActivity)
                }
            }
            DisposableEffect(libraryViewModel) {
                val watcher =
                    com.omnidroid.app.shared.library.LocalFolderWatcher(this@MainActivity) {
                        libraryViewModel.autoSyncLibrary(this@MainActivity)
                    }
                watcher.start()
                onDispose { watcher.stop() }
            }
            val gamepadConnected =
                inputDeviceManager.getGamePadsObservable()
                    .collectAsState(emptyList())
                    .value
                    .any { it.getOmnidroidInputDevice() is OmnidroidInputDeviceGamePad }

            val isGrid = libraryState.layout == LibraryLayout.GRID
            val libraryHints =
                ControllerHints.library(
                    switchConsoles = stringResource(R.string.controller_hint_switch_consoles),
                    zoom = if (isGrid) stringResource(R.string.controller_hint_zoom) else null,
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
                    select = stringResource(R.string.controller_hint_manage),
                    back = stringResource(R.string.controller_hint_back),
                )
            val settingsHints =
                ControllerHints.carousel(
                    switchConsoles = stringResource(R.string.controller_hint_switch_categories),
                    select = stringResource(R.string.controller_hint_select),
                    back = stringResource(R.string.controller_hint_back),
                )

            LaunchedEffect(currentRoute, selectedGameState.value, libraryState.layout) {
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
                if (controllerNav.modalNavigation != null) {
                    controllerNav.modalNavigation?.onBack?.invoke()
                } else if (controllerNav.textInputActive || controllerNav.searchArmed.value) {
                    controllerNav.dismissSearch()
                } else if (controllerNav.modalNavigation != null) {
                    controllerNav.modalNavigation?.onBack?.invoke()
                } else if (currentRoute == MainRoute.GAME_DETAILS) {
                    closeGameDetails()
                } else if (currentRoute == MainRoute.ADD_CONSOLES) {
                    onBackPressedDispatcher.onBackPressed()
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
                    controllerNav.modalNavigation != null -> controllerNav.modalNavigation?.onOptions?.invoke()
                    focusedGame != null -> selectedGameState.value = focusedGame
                    currentRoute != MainRoute.SETTINGS -> navController.navigateToRoute(MainRoute.SETTINGS)
                }
            }
            controllerNav.onSearch = {
                when (currentRoute) {
                    MainRoute.HOME -> { }
                    MainRoute.GAME_DETAILS -> closeGameDetails()
                    else -> navigateToLibraryHome(navController)
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

            val detailsBackIsAPlainPop =
                currentRoute == MainRoute.GAME_DETAILS &&
                    selectedGameState.value == null &&
                    !infoDialogDisplayed.value &&
                    !showCastPicker.value
            BackHandler(enabled = !detailsBackIsAPlainPop) {
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

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val uiScaleStep =
                com.omnidroid.app.utils.settings.rememberSafePreferenceIntSettingState(
                    key = stringResource(id = R.string.pref_key_button_ui_scale_step),
                    defaultValue = 0,
                    preferences = sharedPreferences,
                )
            val uiScale =
                remember(uiScaleStep.value) {
                    com.omnidroid.app.mobile.feature.settings.uiscale.UiScaleHelper.scaleForStep(uiScaleStep.value)
                }
            val adaptiveSpec =
                remember(maxWidth, maxHeight, uiScale) {
                    calculateAdaptiveLayoutSpec(maxWidth, maxHeight, uiScale)
                }
            val portrait = maxHeight > maxWidth
            val portraitColumnWidth = maxWidth
            val libraryTopPadding =
                if (portrait) adaptiveSpec.topBarPortraitHeight else adaptiveSpec.topBarRowHeight
            val standardTopPadding = adaptiveSpec.topBarRowHeight
            CompositionLocalProvider(
                LocalControllerNavigation provides controllerNav,
                LocalAdaptiveLayout provides adaptiveSpec,
            ) {
            val updateState by appUpdater.state.collectAsState()

            Scaffold(
                containerColor = homeChromeColor,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (gamepadConnected && controllerNav.modalNavigation == null) {
                        ControllerHintBar(
                            visible = true,
                            hints = controllerNav.hints.value,
                        )
                    }
                },
            ) { padding ->
                SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        modifier = Modifier.fillMaxSize(),
                        navController = navController,
                        startDestination = MainRoute.HOME.route,
                        predictivePopEnterTransition = { _ ->
                            fadeIn(animationSpec = tween(predictiveFadeMillis(targetState)))
                        },
                        predictivePopExitTransition = { _ ->
                            fadeOut(animationSpec = tween(predictiveFadeMillis(initialState)))
                        },
                    ) {
                        composable(
                            MainRoute.HOME,
                            enterTransition = { fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing)) },
                            exitTransition = { fadeOut(animationSpec = tween(400, easing = FastOutSlowInEasing)) },
                            popEnterTransition = { fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing)) },
                            popExitTransition = { fadeOut(animationSpec = tween(400, easing = FastOutSlowInEasing)) },
                        ) {
                            LibraryScreen(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(padding)
                                        .padding(top = libraryTopPadding),
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this,
                                viewModel = libraryViewModel,
                                onGameClick = onGameClick,
                                onOpenContinueGame = { game ->
                                    lastClickedGame = game
                                    navController.navigateToGameDetails(game.id, GameCoverOrigin.Continue)
                                },
                                onContinueClick = {
                                    libraryViewModel.clearSearchQuery()
                                    gameInteractor.onGamePlay(it)
                                },
                                onGameLongClick = onGameLongClick,
                                onFavoriteToggle = onGameFavoriteToggle,
                                onAddConsole = { navController.navigateToRoute(MainRoute.ADD_CONSOLES) },
                                controllerConnected = gamepadConnected,
                                portraitColumnWidth = portraitColumnWidth,
                                backdropTopBleed = libraryTopPadding,
                            )
                        }
                        composable(
                            MainRoute.GAME_DETAILS,
                            enterTransition = { fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing)) },
                            exitTransition = { fadeOut(animationSpec = tween(400, easing = FastOutSlowInEasing)) },
                            popEnterTransition = { fadeIn(animationSpec = tween(400, easing = FastOutSlowInEasing)) },
                            popExitTransition = { fadeOut(animationSpec = tween(400, easing = FastOutSlowInEasing)) },
                        ) { entry ->
                            val gameId = entry.arguments?.getInt("gameId") ?: return@composable
                            val initialGame = lastClickedGame?.takeIf { it.id == gameId }
                            GameDetailsScreen(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(padding)
                                        .padding(top = standardTopPadding),
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this,
                                coverOrigin = entry.gameCoverOrigin,
                                viewModel =
                                    viewModel(
                                        factory =
                                            GameDetailsViewModel.Factory(
                                                applicationContext,
                                                retrogradeDb,
                                                settingsManager,
                                                gameId,
                                                initialGame = initialGame,
                                            ),
                                    ),
                                onBack = {
                                    navController.popBackStack()
                                },
                                onPlay = {
                                    libraryViewModel.clearSearchQuery()
                                    gameInteractor.onGamePlay(it)
                                },
                                onFavoriteToggle = onGameFavoriteToggle,
                                onOpenSettings = { selectedGameState.value = it },
                                onSetCustomName = { game, name -> gameInteractor.onSetCustomName(game, name) },
                                onSetCustomThumbnail = { game, uri -> gameInteractor.onSetCustomCover(game, uri) },
                                backdropTopBleed = standardTopPadding,
                            )
                        }
                    composable(MainRoute.ADD_CONSOLES) {
                        AddConsoleScreen(
                            initialConsole = consoleRequest?.first,
                            selectionRequest = consoleRequest?.second ?: 0,
                            onSelectionConsumed = { consoleRequest = null },
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .padding(top = standardTopPadding),
                            viewModel =
                                viewModel(
                                    factory =
                                        AddConsoleViewModel.Factory(
                                            applicationContext,
                                            retrogradeDb,
                                            registeredSystemsStore,
                                            coreUpdater,
                                            coresSelection,
                                            libretroDb,
                                            sliceInstaller,
                                        ),
                                ),
                        )
                    }
                    composable(MainRoute.SETTINGS) {
                        SettingsScreen(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .padding(top = standardTopPadding),
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
                            availableConsoles = libraryState.sidebarSystems,
                        )
                    }
                    composable(MainRoute.SETTINGS_UNRECOGNIZED) {
                        SettingsSubscreenContent(
                            subscreen = SettingsSubscreen.UNRECOGNIZED_FILES,
                            key = null,
                            modifier = Modifier.fillMaxSize().padding(padding).padding(top = standardTopPadding),
                        )
                    }
                    composable(MainRoute.SETTINGS_BIOS) {
                        SettingsSubscreenContent(
                            subscreen = SettingsSubscreen.BIOS,
                            key = null,
                            modifier = Modifier.fillMaxSize().padding(padding).padding(top = standardTopPadding),
                        )
                    }
                    composable(MainRoute.SETTINGS_CORES_SELECTION) {
                        SettingsSubscreenContent(
                            subscreen = SettingsSubscreen.CORES_SELECTION,
                            key = null,
                            modifier = Modifier.fillMaxSize().padding(padding).padding(top = standardTopPadding),
                        )
                    }
                    composable(MainRoute.SETTINGS_GRAPHICS_API) {
                        SettingsSubscreenContent(
                            subscreen = SettingsSubscreen.GRAPHICS_API,
                            key = null,
                            modifier = Modifier.fillMaxSize().padding(padding).padding(top = standardTopPadding),
                        )
                    }
                    composable(MainRoute.SETTINGS_INPUT_DEVICES) {
                        SettingsSubscreenContent(
                            subscreen = SettingsSubscreen.INPUT_DEVICES,
                            key = null,
                            modifier = Modifier.fillMaxSize().padding(padding).padding(top = standardTopPadding),
                        )
                    }
                    composable(MainRoute.SETTINGS_SAVE_SYNC) {
                        SettingsSubscreenContent(
                            subscreen = SettingsSubscreen.SAVE_SYNC,
                            key = null,
                            modifier = Modifier.fillMaxSize().padding(padding).padding(top = standardTopPadding),
                        )
                    }
                    composable(MainRoute.SETTINGS_DEVICE_PROFILE) {
                        SettingsSubscreenContent(
                            subscreen = SettingsSubscreen.DEVICE_PROFILE,
                            key = null,
                            modifier = Modifier.fillMaxSize().padding(padding).padding(top = standardTopPadding),
                        )
                    }
                    composable(MainRoute.SETTINGS_UI_SCALE) {
                        com.omnidroid.app.mobile.feature.settings.uiscale.UiScalePreviewScreen(
                            availableConsoles = libraryState.sidebarSystems,
                            onDismiss = { navController.popBackStack() },
                        )
                    }
                    composable(MainRoute.PROFILE) {
                        ProfileScreen(
                            onGameClick = { gameId -> navController.navigateToGameDetails(gameId) },
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .padding(top = standardTopPadding),
                            viewModel =
                                viewModel(
                                    factory =
                                        ProfileViewModel.Factory(
                                            applicationContext,
                                            retrogradeDb,
                                            registeredSystemsStore,
                                        ),
                                ),
                        )
                    }
                }
            }

            LibraryGameMenu(
                selectedGameState = selectedGameState,
                retrogradeDb = retrogradeDb,
                saveSyncSupported = gameInteractor.isSaveSyncSupported(),
                gamepadConnected = gamepadConnected,
                coverSearcher = coverSearcher,
                actions = LibraryGameMenuActions(
                    play = {
                        libraryViewModel.clearSearchQuery()
                        gameInteractor.onGamePlay(it)
                    },
                    restart = {
                        libraryViewModel.clearSearchQuery()
                        gameInteractor.onGameRestart(it)
                    },
                    favorite = { game: Game, isFavorite: Boolean ->
                        gameInteractor.onFavoriteToggle(game, isFavorite)
                    },
                    cloudOverride = { game, override -> gameInteractor.setCloudOverride(game, override) },
                    frameSpeed = { game, speed -> gameInteractor.setFrameSpeed(game, speed) },
                    syncNow = { gameInteractor.syncGameNow(it) },
                    setThumbnail = { game, uri -> gameInteractor.onSetCustomCover(game, uri) },
                    removeThumbnail = { gameInteractor.onRemoveCustomCover(it) },
                    setCoverUrl = { game, url -> gameInteractor.onSetCoverUrl(game, url) },
                    fixMatch = { game, title, url -> gameInteractor.onFixGameMatch(game, title, url) },
                    setName = { game, name -> gameInteractor.onSetCustomName(game, name) },
                    removeName = { gameInteractor.onRemoveCustomName(it) },
                ),
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
                        val result = castDisplayManager.stopCasting()
                        showCastPicker.value = false
                        if (result is StopCastResult.NeedsSystemDisconnect) {
                            castDisplayManager.openSystemCastSettings(this@MainActivity)
                        }
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

                OmnidroidDialog(
                    text = { HtmlText(text = message) },
                    width = DialogWidth.Standard,
                    onDismissRequest = { infoDialogDisplayed.value = false },
                    confirmButton = {
                        OmnidroidDialogButton(onClick = { infoDialogDisplayed.value = false }) {
                            Text(stringResource(R.string.settings_action_close))
                        }
                    },
                )
            }

            if (showWelcomeBenchmark.value) {
                WelcomeBenchmarkDialog(
                    onAgree = {
                        showWelcomeBenchmark.value = false
                        deviceProfileManager.markBenchmarkPromptShown()
                        showExtendedBenchmark.value = true
                        extendedBenchmarkProgress.value = 0f
                        scope.launch {
                            deviceProfileManager.runExtendedBenchmark { progress ->
                                extendedBenchmarkProgress.value = progress
                            }
                            showExtendedBenchmark.value = false
                        }
                    },
                    onDecline = {
                        showWelcomeBenchmark.value = false
                        deviceProfileManager.markBenchmarkPromptShown()
                    },
                )
            }

            if (showExtendedBenchmark.value) {
                ExtendedBenchmarkProgressDialog(progress = extendedBenchmarkProgress.value)
            }

            if (currentRoute != MainRoute.SETTINGS_UI_SCALE) {
                LibraryTopBar(
                    modifier = Modifier.fillMaxSize(),
                    bottomPadding = padding.calculateBottomPadding(),
                    syncProgress = libraryState.syncProgress,
                    gamepadConnected = gamepadConnected,
                    onRandomPressed = {
                        scope.launch { libraryViewModel.randomGame()?.let(openGameDetails) }
                    },
                    sort = libraryState.sort,
                    onSortChange = { libraryViewModel.setSort(it) },
                    onCastPressed = {
                        castDisplayManager.refresh()
                        showCastPicker.value = true
                    },
                    onSettingsPressed = { navController.navigateToRoute(MainRoute.SETTINGS) },
                    onProfilePressed = { navController.navigateToRoute(MainRoute.PROFILE) },
                    searchQuery = libraryState.searchQuery,
                    onSearchQueryChange = { libraryViewModel.changeQueryString(it) },
                    onClearSearch = { libraryViewModel.clearSearchQuery() },
                    casting = castState.isCasting,
                    consoleTitleId =
                        (libraryState.filter as? LibraryFilter.System)?.metaSystemID?.titleResId,
                    consoleGameCount = libraryState.selectedSystemGameCount,
                    portrait = portrait,
                    showLibraryControls = currentRoute == MainRoute.HOME,
                    onBackPressed =
                        if (currentRoute == MainRoute.HOME) {
                            null
                        } else ({
                            if (currentRoute == MainRoute.ADD_CONSOLES) onBackPressedDispatcher.onBackPressed()
                            else navController.popBackStack()
                        }),
                )
            }

            AppUpdateOverlay(
                state = updateState,
                supportsGitHubOptions = appUpdater.supportsGitHubOptions,
                onUpdate = {
                    appUpdater.startUpdate(this@MainActivity, appUpdateLauncher)
                },
                onLater = { appUpdater.dismissAvailable() },
                onSkip = { appUpdater.skipCurrentVersion() },
                onRestart = {
                    if (BuildConfig.GITHUB_UPDATES) {
                        appUpdater.onUnknownSourcesReturned(this@MainActivity)
                    } else {
                        appUpdater.completeFlexibleUpdate()
                    }
                },
                onDismissError = { appUpdater.dismissAvailable() },
                modifier = Modifier.align(Alignment.BottomCenter),
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
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        hideSystemBars()
        castDisplayManager.refresh()
        castDisplayManager.attachHost(this)
        GamePlatformSession.reportLibrary(this)
        appUpdater.onHostResumed(this)
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
        readConsoleRequest(intent)
    }

    private fun predictiveFadeMillis(entry: NavBackStackEntry): Int {
        return when (entry.destination.route) {
            MainRoute.HOME.route, MainRoute.GAME_DETAILS.route -> 400
            else -> 700
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

    override fun isBusy(): Boolean = mainViewModel.state.value.operationInProgress

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            BaseGameActivity.REQUEST_PLAY_GAME -> {
                castDisplayManager.restoreIdle(this)
                lifecycleScope.safeLaunch {
                    gameLaunchTaskHandler.handleGameFinish(
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
