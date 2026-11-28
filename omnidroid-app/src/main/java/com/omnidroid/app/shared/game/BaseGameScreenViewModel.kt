package com.omnidroid.app.shared.game
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.game.GameService
import com.omnidroid.app.mobile.feature.settings.SettingsManager
import com.omnidroid.app.shared.game.backend.libretro.LibretroBackendSession
import com.omnidroid.app.shared.game.view.IRetroGameView
import com.omnidroid.app.shared.game.viewmodel.GameViewModelInput
import com.omnidroid.app.shared.game.viewmodel.GameViewModelRetroGameView
import com.omnidroid.app.shared.game.viewmodel.GameViewModelSaves
import com.omnidroid.app.shared.game.viewmodel.GameViewModelSideEffects
import com.omnidroid.app.shared.game.viewmodel.GameViewModelTilt
import com.omnidroid.app.shared.game.viewmodel.GameViewModelTouchControls
import com.omnidroid.app.shared.input.InputDeviceManager
import com.omnidroid.app.shared.ra.PreparedRaContent
import com.omnidroid.app.shared.ra.RaContentResolverDelegate
import com.omnidroid.app.shared.ra.RetroAchievementsCoordinator
import com.omnidroid.app.shared.rumble.RumbleManager
import com.omnidroid.app.shared.settings.ControllerConfigsManager
import com.omnidroid.app.shared.settings.HapticFeedbackMode
import com.omnidroid.common.longAnimationDuration
import com.omnidroid.lib.backend.BackendCapabilities
import com.omnidroid.lib.backend.BackendID
import com.omnidroid.lib.backend.BackendSession
import com.omnidroid.lib.backend.ConsoleBackendRegistry
import com.omnidroid.lib.controller.ControllerConfig
import com.omnidroid.lib.core.CoreVariablesManager
import com.omnidroid.lib.game.GameLoader
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.ra.HardcoreBlockReason
import com.omnidroid.lib.ra.HardcoreDecision
import com.omnidroid.lib.ra.HardcorePolicy
import com.omnidroid.lib.ra.RaCoreCatalog
import com.omnidroid.lib.ra.RaCoreResolution
import com.omnidroid.lib.ra.RaGameInfo
import com.omnidroid.lib.ra.RaPauseDecision
import com.omnidroid.lib.ra.RetroAchievementsEngine
import com.omnidroid.lib.ra.RetroAchievementsEvent
import com.omnidroid.lib.saves.SavesManager
import com.omnidroid.lib.saves.StatesManager
import com.omnidroid.lib.saves.StatesPreviewManager
import com.omnidroid.lib.savesync.GameFrameSpeedPreferences
import com.omnidroid.libretrodroid.GLRetroView
import com.omnidroid.touchinput.radial.sensors.TiltConfiguration
import com.omnidroid.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.inputevents.InputEvent
import gg.padkit.inputstate.InputState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class BaseGameScreenViewModel(
    private val appContext: Context,
    val game: Game,
    private val settingsManager: SettingsManager,
    inputDeviceManager: InputDeviceManager,
    controllerConfigsManager: ControllerConfigsManager,
    val system: GameSystem,
    val systemCoreConfig: SystemCoreConfig,
    private val sharedPreferences: SharedPreferences,
    savesManager: SavesManager,
    statesManager: StatesManager,
    statesPreviewManager: StatesPreviewManager,
    coreVariablesManager: CoreVariablesManager,
    rumbleManager: RumbleManager,
    val raCoordinator: RetroAchievementsCoordinator,
) : ViewModel(),
    DefaultLifecycleObserver {
    var activeSession: BackendSession? = null

        private set

    val hasTouchScreen: Boolean = system.hasTouchScreen

    val hardcorePolicy: HardcorePolicy
        get() = raCoordinator.hardcorePolicy

    val raEngine: RetroAchievementsEngine
        get() = raCoordinator.engine

    private val _raCoreResolution = MutableStateFlow<RaCoreResolution?>(null)
    val raCoreResolution: StateFlow<RaCoreResolution?> = _raCoreResolution.asStateFlow()

    @Volatile
    private var preparedRaContent: PreparedRaContent? = null

    private var raLoadJob: Job? = null
    private var raIdleJob: Job? = null
    private var wasRaDisconnected: Boolean = false

    init {
        startRaEventCollector()
    }

    class Factory(
        private val appContext: Context,
        private val game: Game,
        private val settingsManager: SettingsManager,
        private val inputDeviceManager: InputDeviceManager,
        private val controllerConfigsManager: ControllerConfigsManager,
        private val system: GameSystem,
        private val systemCoreConfig: SystemCoreConfig,
        private val sharedPreferences: SharedPreferences,
        private val savesManager: SavesManager,
        private val statesManager: StatesManager,
        private val statesPreviewManager: StatesPreviewManager,
        private val coreVariablesManager: CoreVariablesManager,
        private val rumbleManager: RumbleManager,
        private val raCoordinator: RetroAchievementsCoordinator,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BaseGameScreenViewModel(
                appContext,
                game,
                settingsManager,
                inputDeviceManager,
                controllerConfigsManager,
                system,
                systemCoreConfig,
                sharedPreferences,
                savesManager,
                statesManager,
                statesPreviewManager,
                coreVariablesManager,
                rumbleManager,
                raCoordinator,
            ) as T
    }

    private val sideEffects = GameViewModelSideEffects(viewModelScope)

    val retroGameView =

        GameViewModelRetroGameView(
            appContext,
            system,
            systemCoreConfig,
            settingsManager,
            coreVariablesManager,
            sideEffects,
            rumbleManager,
            viewModelScope,
        )

    private val tilt = GameViewModelTilt(appContext, settingsManager)

    private val inputs =

        GameViewModelInput(
            appContext,
            system,
            systemCoreConfig,
            inputDeviceManager,
            controllerConfigsManager,
            retroGameView,
            tilt,
            sideEffects,
            viewModelScope,
        )

    private val touchControls =

        GameViewModelTouchControls(
            appContext,
            settingsManager,
            TouchControllerSettingsManager(sharedPreferences),
            retroGameView,
            inputs,
            tilt,
            sideEffects,
            viewModelScope,
            system.id,
            systemCoreConfig,
            sharedPreferences,
            system.hasTouchScreen,
        )

    private val saves =

        GameViewModelSaves(
            appContext,
            system,
            game,
            systemCoreConfig,
            retroGameView,
            settingsManager,
            savesManager,
            statesManager,
            statesPreviewManager,
            sideEffects,
            raCoordinator.hardcorePolicy,
            raCoordinator.engine,
        )

    val loadingState = MutableStateFlow(false)

    private inline fun withLoading(block: () -> Unit) {
        loadingState.value = true

        try {
            block()
        } finally {
            loadingState.value = false
        }
    }

    fun getGameState(): Flow<GameViewModelRetroGameView.GameState> = retroGameView.getGameState()

    fun getSideEffects(): Flow<GameViewModelSideEffects.UiEffect> = sideEffects.getUiEffects()

    fun getTiltConfiguration(): Flow<TiltConfiguration> = tilt.getTiltConfiguration()

    fun getSimulatedTiltEvents(): Flow<InputState> = tilt.getSimulatedTiltEvents()

    fun getTouchControlsSettings(): Flow<TouchControllerSettingsManager.Settings?> =
        touchControls.getTouchControlsSettings()

    fun getTouchHapticFeedbackMode(): Flow<HapticFeedbackMode> = touchControls.getTouchHapticFeedbackMode()

    fun getTouchVibrationIntensity(): Flow<Float> = touchControls.getTouchVibrationIntensity()

    fun getVirtualPadOpacity(): Flow<Float> = touchControls.getVirtualPadOpacity()

    suspend fun loadNativeGame(
        activity: Activity,
        systemCoreConfig: SystemCoreConfig,
        gameLoader: GameLoader,
    ) {
        val descriptor = requireNotNull(ConsoleBackendRegistry.findDescriptorForCore(systemCoreConfig.coreID))
        retroGameView.setLoadingForNative("Loading ${descriptor.displayName}…")
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                gameLoader.ensureCoreDownloaded(activity.applicationContext, systemCoreConfig.coreID)
            }
            val runtime = requireNotNull(ConsoleBackendRegistry.getRuntime(activity, descriptor.id))
            runtime.prepare(activity, game, systemCoreConfig).getOrThrow()
            retroGameView.setReadyForNative()
        } catch (failure: kotlinx.coroutines.CancellationException) {
            throw failure
        } catch (failure: Exception) {
            Timber.e(failure, "Failed to load native backend")
            sideEffects.requestFailureFinish(failure.message ?: "Failed to load ${descriptor.displayName}")
        }
    }

    fun createGameView(
        context: Context,
        lifecycle: LifecycleOwner,
    ): View {
        val playbackOwner = (context as? BaseGameActivity)?.playbackLifecycle ?: lifecycle
        val descriptor = ConsoleBackendRegistry.findDescriptorForCore(systemCoreConfig.coreID)

        val capabilities = descriptor?.capabilities(systemCoreConfig) ?: BackendCapabilities()

        if (descriptor != null && descriptor.id != BackendID.LIBRETRO) {
            val activity = context as? Activity ?: throw IllegalStateException("Context must be an Activity")

            val runtime = requireNotNull(ConsoleBackendRegistry.getRuntime(context, descriptor.id))
            val session =
                runtime.createSession(
                    activity,
                    game,
                    systemCoreConfig,
                    android.widget.FrameLayout(activity),
                ) { resultCode ->
                    if (resultCode != 0) {
                        sideEffects.requestFailureFinish("Emulation exited with code $resultCode")
                    } else if (activeSession is com.omnidroid.lib.backend.NativeSaveSession) {
                        requestFinish()
                    } else {
                        sideEffects.requestSuccessfulFinish()
                    }
                }
            val gameView = session as IRetroGameView

            activeSession = session

            retroGameView.retroGameView = gameView
            gameView.disableTouchEvents()

            playbackOwner.lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onPause(owner: LifecycleOwner) {
                        session.pause()
                    }

                    override fun onResume(owner: LifecycleOwner) {
                        session.resume()
                    }

                    override fun onDestroy(owner: LifecycleOwner) {
                        session.stop()
                    }
                },
            )

            return session.view
        } else {
            val retroView = createRetroView(context, playbackOwner)

            val res = _raCoreResolution.value
            val session =
                LibretroBackendSession(
                    retroGameView = retroView,
                    capabilities =
                        ConsoleBackendRegistry
                            .findDescriptorForCore(
                                systemCoreConfig.coreID,
                            )?.capabilities(systemCoreConfig)
                            ?: BackendCapabilities(),
                    hardcorePolicy = raCoordinator.hardcorePolicy,
                    onHardcoreBlocked = { blocked -> showHardcoreBlockedToast(blocked) },
                    libraryNameProvider = {
                        res?.libraryName?.ifBlank { null } ?: raCoordinator.engine.getLoadedLibraryName()
                    },
                    consoleId = res?.consoleId ?: 0,
                )

            activeSession = session
            attachRetroAchievementsIfReady(retroView, playbackOwner)

            return retroView.asView()
        }
    }

    fun createRetroView(
        context: Context,
        lifecycle: LifecycleOwner,
    ): IRetroGameView {
        val (gameData, result) = retroGameView.createRetroView(context, lifecycle)

        viewModelScope.launch {
            gameData.autoSaveLoadError?.let { saves.onAutoSaveLoadFailed(it) }

            gameData.quickSaveData?.let {
                saves.restoreAutoSaveAsync(it)
            }
        }

        return result
    }

    suspend fun loadGame(
        applicationContext: Context,
        game: Game,
        systemCoreConfig: SystemCoreConfig,
        gameLoader: GameLoader,
        requestLoadSave: Boolean,
    ) {
        Timber.i("Calling load game: $game")

        prepareRetroAchievementsPreLoad(systemCoreConfig)
        retroGameView.initialize(applicationContext, game, systemCoreConfig, gameLoader, requestLoadSave)
    }

    private suspend fun prepareRetroAchievementsPreLoad(systemCoreConfig: SystemCoreConfig) {
        val raEnabled = settingsManager.raEnabled()
        val preliminaryResolution =
            RaCoreCatalog.resolve(
                coreID = systemCoreConfig.coreID,
                systemID = system.id,
                engine = null,
            )
        _raCoreResolution.value = preliminaryResolution

        if (!raEnabled || !preliminaryResolution.supported || !raCoordinator.isSignedIn()) {
            raCoordinator.hardcorePolicy.clearSession()
            retroGameView.raCoreOptionValidator = null
            return
        }

        val moduleResult =
            raCoordinator.ensureModuleLoaded(
                coreLabel = "${systemCoreConfig.coreID.coreName}/${systemCoreConfig.coreID.libretroFileName}",
                downloadIfMissing = com.omnidroid.BuildConfig.GITHUB_UPDATES,
            )
        val engine = raCoordinator.engine
        val moduleLoaded = moduleResult.isSuccess && engine.isModuleLoaded()
        if (moduleLoaded) {
            raCoordinator.silentLoginIfNeeded(systemCoreConfig.coreID.coreName)
            raCoordinator.syncSettingsToEngine()
        }
        val resolution =
            RaCoreCatalog.resolve(
                coreID = systemCoreConfig.coreID,
                systemID = system.id,
                engine = if (moduleLoaded) engine else null,
            )
        _raCoreResolution.value = resolution

        val loggedIn = moduleLoaded && engine.getUserInfo() != null
        val hardcoreRequested = settingsManager.raHardcore()
        raCoordinator.hardcorePolicy.updateSession(
            moduleLoaded = moduleLoaded,
            loggedIn = loggedIn,
            raEnabled = raEnabled,
            hardcoreRequested = hardcoreRequested,
            gameLoaded = false,
            coreSupported = resolution.supported,
        )
        retroGameView.raCoreOptionValidator = { key, value ->
            raCoordinator.hardcorePolicy.canApplyCoreOption(
                libraryName = resolution.libraryName.ifBlank { engine.getLoadedLibraryName() },
                consoleId = resolution.consoleId,
                key = key,
                value = value,
            ) is HardcoreDecision.Allowed
        }
    }

    private fun attachRetroAchievementsIfReady(
        retroView: IRetroGameView,
        lifecycle: LifecycleOwner,
    ) {
        val resolution = _raCoreResolution.value ?: return
        val sessionState = raCoordinator.hardcorePolicy.sessionState.value
        val engine = raCoordinator.engine
        if (!sessionState.moduleLoaded || !sessionState.loggedIn || !resolution.supported) return

        if (raCoordinator.hardcorePolicy.isHardcoreActive && retroView.frameSpeed < 1) {
            retroView.frameSpeed = 1
        }

        engine.attachToLibretroSession(resolution.consoleId)

        raLoadJob?.cancel()
        raLoadJob =
            viewModelScope.launch(Dispatchers.IO) {
                val gameData = retroGameView.currentGameData() ?: return@launch
                val dataFiles = raCoordinator.dataFilesForGame(game.id)
                val prepared = RaContentResolverDelegate.prepare(appContext, game, gameData, dataFiles) ?: return@launch
                preparedRaContent = prepared

                val loadResult =
                    engine.identifyAndLoadGame(
                        consoleId = resolution.consoleId,
                        filePath = prepared.primaryPath,
                        fileOpenDelegate = prepared.openDelegate,
                    )
                loadResult
                    .onSuccess { raGame ->
                        raCoordinator.hardcorePolicy.updateSession(gameLoaded = true)
                        raCoordinator.persistGameCache(game.id, raGame)
                    }.onFailure { err ->
                        Timber.w(err, "RetroAchievements identify/load failed for %s", game.title)
                    }

                runCatching {
                    retroGameView.waitGLEvent<GLRetroView.GLRetroEvents.FrameRendered>()
                    engine.refreshMemoryMap(resolution.consoleId)
                }
            }

        lifecycle.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onPause(owner: LifecycleOwner) {
                    if (!engine.isModuleLoaded()) return
                    raIdleJob?.cancel()
                    raIdleJob =
                        viewModelScope.launch {
                            while (isActive && engine.isModuleLoaded()) {
                                engine.idle()
                                delay(450L)
                            }
                        }
                }

                override fun onResume(owner: LifecycleOwner) {
                    raIdleJob?.cancel()
                    raIdleJob = null
                    viewModelScope.launch {
                        raCoordinator.syncSettingsToEngine()
                        enforceHardcoreRuntimeLimits(retroView)
                    }
                }
            },
        )
    }

    private fun startRaEventCollector() {
        viewModelScope.launch {
            raCoordinator.engine.events.collect { event ->
                when (event) {
                    is RetroAchievementsEvent.ResetRequired -> {
                        sideEffects.showToast(appContext.getString(R.string.ra_reset_required_banner))
                        reset()
                    }
                    is RetroAchievementsEvent.AchievementTriggered,
                    is RetroAchievementsEvent.GameCompleted,
                    is RetroAchievementsEvent.SubsetCompleted,
                    -> {
                        val info: RaGameInfo? = raCoordinator.engine.activeGame.value
                        if (info != null) {
                            runCatching { raCoordinator.persistGameCache(game.id, info) }
                        }
                    }
                    is RetroAchievementsEvent.Disconnected -> {
                        if (!wasRaDisconnected) {
                            wasRaDisconnected = true
                            sideEffects.showToast(appContext.getString(R.string.ra_disconnected_warning))
                        }
                    }
                    is RetroAchievementsEvent.Reconnected -> {
                        if (wasRaDisconnected) {
                            wasRaDisconnected = false
                            sideEffects.showToast(appContext.getString(R.string.ra_reconnected_notice))
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun enforceHardcoreRuntimeLimits(retroView: IRetroGameView) {
        if (!raCoordinator.hardcorePolicy.isHardcoreActive) return
        if (retroView.frameSpeed < 1) {
            retroView.frameSpeed = 1
        }
        retroGameView.refreshCoreVariables()
    }

    fun canOpenMenu(): Boolean =
        when (val decision = raCoordinator.hardcorePolicy.canPauseGame()) {
            is RaPauseDecision.Allowed -> true
            is RaPauseDecision.Cooldown -> {
                val secs = ((decision.framesRemaining + 59) / 60).coerceAtLeast(1)
                sideEffects.showToast(appContext.getString(R.string.ra_pause_cooldown_toast, secs))
                false
            }
        }

    fun requestMenu() {
        sideEffects.showMenu(tilt, inputs)
    }

    fun applyFrameSpeed(requestedSpeed: Int) {
        val decision = raCoordinator.hardcorePolicy.canSetFrameSpeedMultiplier(requestedSpeed.toFloat())
        if (decision is HardcoreDecision.Blocked) {
            showHardcoreBlockedToast(decision)
            retroGameView.retroGameView?.frameSpeed = 1
            GameFrameSpeedPreferences(appContext).set(game.id, 1)
            return
        }
        retroGameView.retroGameView?.frameSpeed = requestedSpeed
        GameFrameSpeedPreferences(appContext).set(game.id, requestedSpeed)
    }

    fun onDiscChanged(discIndex: Int) {
        retroGameView.retroGameView?.changeDisk(discIndex, false)
        val prepared = preparedRaContent ?: return
        if (!raCoordinator.engine.isModuleLoaded()) return
        val discPath = RaContentResolverDelegate.resolveDiscPath(appContext, discIndex, game, prepared) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            raCoordinator.engine
                .identifyAndChangeMedia(
                    filePath = discPath,
                    fileOpenDelegate = prepared.openDelegate,
                ).onSuccess { raGame ->
                    raCoordinator.persistGameCache(game.id, raGame)
                }
        }
    }

    fun setHardcoreModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            raCoordinator.setHardcorePreference(enabled)
            raCoordinator.hardcorePolicy.updateSession(hardcoreRequested = enabled)
            retroGameView.retroGameView?.let { enforceHardcoreRuntimeLimits(it) }
        }
    }

    private fun showHardcoreBlockedToast(blocked: HardcoreDecision.Blocked) {
        val resId =
            when (blocked.reason) {
                HardcoreBlockReason.LOAD_AUTO_SAVE_ON_LAUNCH -> R.string.ra_hardcore_autosave_skipped
                HardcoreBlockReason.LOAD_SAVE_STATE -> R.string.ra_hardcore_blocked_load
                HardcoreBlockReason.REWIND -> R.string.ra_hardcore_blocked_rewind
                HardcoreBlockReason.SLOW_MOTION -> R.string.ra_hardcore_blocked_slow_motion
                HardcoreBlockReason.CHEATS,
                HardcoreBlockReason.MEMORY_EDIT,
                -> R.string.ra_hardcore_blocked_cheats
                HardcoreBlockReason.DISALLOWED_CORE_OPTION -> {
                    sideEffects.showToast(blocked.detail)
                    return
                }
            }
        sideEffects.showToast(appContext.getString(resId))
    }

    override fun onCleared() {
        raIdleJob?.cancel()
        raLoadJob?.cancel()
        runCatching { raCoordinator.engine.detachFromLibretroSession() }
        runCatching { raCoordinator.engine.unloadGame() }
        raCoordinator.hardcorePolicy.clearSession()
        super.onCleared()
    }

    fun showEditControls(show: Boolean) {
        touchControls.showEditControls(show)
    }

    fun isEditControlShown(): Flow<Boolean> = touchControls.isEditControlsShown()

    fun updateTouchControllerSettings(touchControllerSettings: TouchControllerSettingsManager.Settings) {
        touchControls.updateTouchControllerSettings(touchControllerSettings)
    }

    fun resetTouchControls() {
        touchControls.resetTouchControls()
    }

    fun onScreenOrientationChanged(orientation: TouchControllerSettingsManager.Orientation) {
        touchControls.updateScreenOrientation(orientation)
    }

    fun isSingleScreenLayout() = touchControls.isSingleScreenLayout()

    fun getGameAspectRatio(): Flow<Float> = touchControls.getGameAspectRatio()

    fun getTouchScreenRegion() = touchControls.getTouchScreenRegion()

    fun cycleScreenLayout() = touchControls.cycleScreenLayout()

    fun refreshSettings() {
        touchControls.refreshLayout()

        retroGameView.refreshCoreVariables()

        inputs.refreshControllers()
    }

    fun isTouchControllerVisible(): Flow<Boolean> = touchControls.isTouchControllerVisible()

    fun isForceHideTouchControls(): Flow<Boolean> = touchControls.isForceHideTouchControls()

    fun showTouchControls() {
        touchControls.showTouchControls()
    }

    fun getTouchControllerConfig(): Flow<ControllerConfig> = touchControls.getTouchControllerConfig()

    fun changeTiltConfiguration(tiltConfig: TiltConfiguration) {
        tilt.changeTiltConfiguration(tiltConfig)
    }

    fun isMenuPressed(): Flow<Boolean> = touchControls.isMenuPressed()

    suspend fun saveSlot(index: Int) {
        if (loadingState.value) return

        withLoading {
            saves.saveSlot(index)
        }
    }

    suspend fun loadSlot(index: Int) {
        if (loadingState.value) return

        withLoading {
            saves.loadSlot(index)
        }
    }

    suspend fun saveNewSlot() {
        if (loadingState.value) return
        withLoading { saves.saveNewSlot() }
    }

    suspend fun loadAutoSave() {
        if (loadingState.value) return
        withLoading { saves.loadAutoSave() }
    }

    fun saveQuickSave() {
        Timber.d("Saving quick save")

        if (loadingState.value) return

        viewModelScope.launch { withLoading { saves.saveQuickSave() } }
    }

    fun loadQuickSave() {
        Timber.d("Loading quick save")

        if (loadingState.value) return

        viewModelScope.launch { withLoading { saves.loadQuickSave() } }
    }

    fun toggleFastForward() {
        retroGameView.retroGameView?.apply {
            val cycle = listOf(1, 2, 4)

            val index = cycle.indexOf(frameSpeed)

            frameSpeed = if (index >= 0) cycle[(index + 1) % cycle.size] else 1
        }
    }

    suspend fun reset() =

        withLoading {
            try {
                delay(appContext.longAnimationDuration().toLong())

                retroGameView.retroGameViewFlow().reset()
            } catch (e: Throwable) {
                Timber.e(e, "Error in reset")
            }
        }

    fun requestFinish() {
        if (loadingState.value) return

        viewModelScope.launch {
            withLoading {
                val nativeSaves = activeSession as? com.omnidroid.lib.backend.NativeSaveSession
                if (nativeSaves != null) {
                    try {
                        nativeSaves.persistNativeSaves(stop = true)
                        sideEffects.requestSuccessfulFinish()
                    } catch (failure: Exception) {
                        Timber.e(failure, "Could not back up PS3 saves")
                        sideEffects.requestSaveFailureOnQuit()
                    }
                    return@withLoading
                }
                if (activeSession !is com.omnidroid.lib.backend.StateSaveableSession) {
                    sideEffects.requestSuccessfulFinish()

                    return@withLoading
                }

                val snapshot = saves.captureSaveSnapshot(true)

                if (!saves.writeSaveSnapshot(snapshot, notifyFailure = false)) {
                    sideEffects.requestSaveFailureOnQuit()
                    return@withLoading
                }

                sideEffects.requestSuccessfulFinish()
            }
        }
    }

    fun requestBackgroundSave() {
        if (loadingState.value ||
            (
                activeSession !is com.omnidroid.lib.backend.StateSaveableSession &&
                    activeSession !is com.omnidroid.lib.backend.NativeSaveSession
            )
        ) {
            return
        }

        GameService.schedule {
            val nativeSaves = activeSession as? com.omnidroid.lib.backend.NativeSaveSession
            if (nativeSaves != null) {
                try {
                    nativeSaves.persistNativeSaves()
                } catch (
                    failure: Exception,
                ) {
                    Timber.e(failure, "Could not back up native saves")
                }
                return@schedule
            }
            // Serialize between frames, including when the UI is returning. Calling
            // a core directly from this service thread races its renderer and GPU.
            val snapshot = saves.captureSaveSnapshot(true)

            saves.writeSaveSnapshot(snapshot)
        }
    }

    fun requestFinishWithoutSaving() {
        if (!loadingState.value) sideEffects.requestSuccessfulFinish()
    }

    fun handleVirtualInputEvent(events: List<InputEvent>) {
        touchControls.handleVirtualInputEvent(events)
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        owner.lifecycle.addObserver(tilt)

        owner.lifecycle.addObserver(inputs)

        owner.lifecycle.addObserver(retroGameView)

        owner.lifecycle.addObserver(touchControls)
    }

    fun sendKeyEvent(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean = inputs.sendKeyEvent(keyCode, event)

    fun sendMotionEvent(event: MotionEvent): Boolean = inputs.sendMotionEvent(event)
}
