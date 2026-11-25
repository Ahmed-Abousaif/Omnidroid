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
import com.omnidroid.lib.saves.SavesManager
import com.omnidroid.lib.saves.StatesManager
import com.omnidroid.lib.saves.StatesPreviewManager
import com.omnidroid.touchinput.radial.sensors.TiltConfiguration
import com.omnidroid.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.inputevents.InputEvent
import gg.padkit.inputstate.InputState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class BaseGameScreenViewModel(
    private val appContext: Context,
    val game: Game,
    settingsManager: SettingsManager,
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
) : ViewModel(),
    DefaultLifecycleObserver {
    var activeSession: BackendSession? = null

        private set

    val hasTouchScreen: Boolean = system.hasTouchScreen

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

            lifecycle.lifecycle.addObserver(
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
            val retroView = createRetroView(context, lifecycle)

            val session =
                LibretroBackendSession(
                    retroGameView = retroView,
                    capabilities =
                        ConsoleBackendRegistry
                            .findDescriptorForCore(
                                systemCoreConfig.coreID,
                            )?.capabilities(systemCoreConfig)
                            ?: BackendCapabilities(),
                )

            activeSession = session

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

        retroGameView.initialize(applicationContext, game, systemCoreConfig, gameLoader, requestLoadSave)
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
            val snapshot = saves.captureSaveSnapshot(false)

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
