package com.omnidroid.app.shared.game.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.omnidroid.BuildConfig
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.settings.SettingsManager
import com.omnidroid.app.shared.game.ShaderChooser
import com.omnidroid.app.shared.game.view.GLRetroGameViewWrapper
import com.omnidroid.app.shared.game.view.IRetroGameView
import com.omnidroid.app.shared.game.view.VulkanRetroView
import com.omnidroid.app.shared.rumble.RumbleManager
import com.omnidroid.app.shared.settings.HDModeQuality
import com.omnidroid.common.coroutines.MutableStateProperty
import com.omnidroid.common.coroutines.launchOnState
import com.omnidroid.common.view.disableTouchEvents
import com.omnidroid.lib.core.CoreVariable
import com.omnidroid.lib.core.CoreVariablesManager
import com.omnidroid.lib.game.GameLoader
import com.omnidroid.lib.game.GameLoaderError
import com.omnidroid.lib.game.GameLoaderException
import com.omnidroid.lib.core.CoreAssets
import com.omnidroid.lib.library.metaSystemID
import com.omnidroid.lib.graphics.VulkanDetector
import com.omnidroid.lib.library.GameSystem
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.lib.savesync.GameFrameSpeedPreferences
import com.omnidroid.lib.storage.RomFiles
import com.omnidroid.libretrodroid.GLRetroView
import com.omnidroid.libretrodroid.GLRetroViewData
import com.omnidroid.libretrodroid.ImmersiveMode
import com.omnidroid.libretrodroid.Variable
import com.omnidroid.libretrodroid.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
class GameViewModelRetroGameView(
    private val appContext: Context,
    private val system: GameSystem,
    private val systemCoreConfig: SystemCoreConfig,
    private val settingsManager: SettingsManager,
    private val coreVariablesManager: CoreVariablesManager,
    private val sideEffects: GameViewModelSideEffects,
    private val rumbleManager: RumbleManager,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    sealed interface GameState {
        data object Uninitialized : GameState

        data class Loading(val message: String) : GameState

        data class Loaded(
            val gameData: GameLoader.GameData,
            val retroViewData: GLRetroViewData,
        ) : GameState

        data object Ready : GameState
    }

    private val gameState: MutableStateFlow<GameState> = MutableStateFlow(GameState.Uninitialized)

    private val retroGameViewFlow = MutableStateFlow<IRetroGameView?>(null)
    var retroGameView: IRetroGameView? by MutableStateProperty(retroGameViewFlow)
    private val frameSpeedPreferences = GameFrameSpeedPreferences(appContext)
    private var rendererSettingsGuard: RendererSettingsGuard? = null

    @Volatile
    var raCoreOptionValidator: ((key: String, value: String) -> Boolean)? = null

    fun currentGameData(): GameLoader.GameData? =
        when (val state = gameState.value) {
            is GameState.Loaded -> state.gameData
            else -> loadedGameDataSnapshot
        }

    @Volatile
    private var loadedGameDataSnapshot: GameLoader.GameData? = null

    fun getGameState(): Flow<GameState> = gameState.debounce(200)

    fun setLoadingForNative(message: String) {
        gameState.value = GameState.Loading(message)
    }

    fun setReadyForNative() {
        gameState.value = GameState.Ready
    }

    suspend fun initialize(
        applicationContext: Context,
        game: Game,
        systemCoreConfig: SystemCoreConfig,
        gameLoader: GameLoader,
        requestLoadSave: Boolean,
    ) {
        val currentState = gameState.value
        if (currentState != GameState.Uninitialized) return

        val autoSaveEnabled = settingsManager.autoSave()
        val filter = settingsManager.screenFilter()
        val hdMode = settingsManager.hdMode()
        val hdModeQuality = settingsManager.hdModeQuality()
        val lowLatencyAudio = settingsManager.lowLatencyAudio()
        val enableRumble = settingsManager.enableRumble()
        val directLoad = settingsManager.allowDirectGameLoad()
        val enableImmersiveMode = settingsManager.enableImmersiveMode()

        val hasMicrophonePermission =
            ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED

        val enableMicrophone = systemCoreConfig.supportsMicrophone && hasMicrophonePermission

        val loadingStatesFlow =
            gameLoader.load(
                applicationContext,
                game,
                requestLoadSave && autoSaveEnabled,
                systemCoreConfig,
                directLoad,
            )

        loadingStatesFlow
            .flowOn(Dispatchers.IO)
            .catch {
                val message =
                    if (it is GameLoaderException) {
                        getErrorMessage(it.error)
                    } else {
                        ""
                    }
                if (it is GameLoaderException &&
                    (it.error is GameLoaderError.MissingAssets || it.error is GameLoaderError.CoreUninstalled)
                ) {
                    sideEffects.requestConsoleSetup(message)
                } else {
                    sideEffects.requestFailureFinish(message)
                }
            }
            .debounce(200)
            .collect { loadingState ->
                gameState.value =
                    if (loadingState is GameLoader.LoadingState.Ready) {
                        Timber.i("Setting state to loaded")
                        val retroViewData =
                            buildRetroViewData(
                                applicationContext,
                                systemCoreConfig,
                                loadingState.gameData,
                                hdMode,
                                hdModeQuality,
                                filter,
                                lowLatencyAudio,
                                enableRumble,
                                enableMicrophone,
                                enableImmersiveMode,
                            )
                        GameState.Loaded(
                            gameData = loadingState.gameData,
                            retroViewData = retroViewData,
                        )
                    } else {
                        GameState.Loading(getLoadingMessage(loadingState))
                    }
            }
    }

    fun createRetroView(
        context: Context,
        lifecycle: LifecycleOwner,
    ): Pair<GameLoader.GameData, IRetroGameView> {
        val (gameData, viewData) =
            run {
                val currentState = gameState.value
                if (currentState !is GameState.Loaded) throw IllegalStateException("Game is not loaded.")
                loadedGameDataSnapshot = currentState.gameData
                currentState.gameData to currentState.retroViewData
            }

        rendererSettingsGuard = RendererSettingsGuard(gameData.coreVariables.asList())

        val isVulkanPreferred =
            isVulkanRequested(gameData) && VulkanDetector.isVulkanSupported(context)

        val retroView: IRetroGameView =
            if (isVulkanPreferred) {
                Timber.i("Initializing VulkanRetroView for core ${systemCoreConfig.coreID.coreName}")
                VulkanRetroView(context, viewData).apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                }
            } else {
                Timber.i("Initializing GLRetroView for core ${systemCoreConfig.coreID.coreName}")
                GLRetroGameViewWrapper(
                    GLRetroView(context, viewData).apply {
                        isFocusable = false
                        isFocusableInTouchMode = false
                    },
                )
            }

        // Framework touch is disabled for all systems. Touchscreen consoles (NDS/3DS) receive
        // stylus input via GameTouchInput, which supports a second finger while
        // virtual buttons are held. GLRetroView.onTouchEvent only handles the primary pointer.
        retroView.disableTouchEvents()

        // Apply after addObserver: LibretroDroid.create() (ON_CREATE) always resets frameSpeed to 1.
        lifecycle.lifecycle.addObserver(retroView)

        if (system.fastForwardSupport) {
            retroView.frameSpeed = frameSpeedPreferences.get(gameData.game.id)
        }

        if (BuildConfig.DEBUG) {
            runCatching {
                printRetroVariables(retroView)
            }
        }

        retroGameViewFlow.value = retroView
        gameState.value = GameState.Ready

        return gameData to retroView
    }

    private fun isVulkanRequested(gameData: GameLoader.GameData): Boolean =
        RendererSettingsGuard.requestsVulkan(gameData.coreVariables.asList())

    suspend fun retroGameViewFlow() =
        retroGameViewFlow
            .filterNotNull()
            .first()

    suspend fun waitRetroGameViewInitialized() {
        retroGameViewFlow()
    }

    suspend inline fun <reified T> waitGLEvent() {
        val retroView = retroGameViewFlow()
        retroView.getGLRetroEvents()
            .filterIsInstance<T>()
            .first()
    }

    private fun buildRetroViewData(
        appContext: Context,
        systemCoreConfig: SystemCoreConfig,
        gameData: GameLoader.GameData,
        hdMode: Boolean,
        hdModeQuality: HDModeQuality,
        screenFilter: String,
        lowLatencyAudio: Boolean,
        requestRumble: Boolean,
        requestMicrophone: Boolean,
        enableImmersiveMode: Boolean,
    ): GLRetroViewData {
        return GLRetroViewData(appContext).apply {
            coreFilePath = gameData.coreLibrary

            when (val gameFiles = gameData.gameFiles) {
                is RomFiles.Standard -> {
                    gameFilePath = gameFiles.files.first().absolutePath
                }

                is RomFiles.Virtual -> {
                    gameVirtualFiles = gameFiles.files.map { VirtualFile(it.filePath, it.fd) }
                }
            }

            systemDirectory = gameData.systemDirectory.absolutePath
            savesDirectory = gameData.savesDirectory.absolutePath
            val validator = raCoreOptionValidator
            val allowedInitialVars =
                if (validator != null) {
                    gameData.coreVariables.filter { validator(it.key, it.value) }
                } else {
                    gameData.coreVariables.asList()
                }
            variables = allowedInitialVars.map { Variable(it.key, it.value) }.toTypedArray()
            saveRAMState = gameData.saveRAMData
            shader =
                ShaderChooser.getShaderForSystem(
                    appContext,
                    hdMode,
                    hdModeQuality,
                    screenFilter,
                    GameSystem.findById(gameData.game.systemId),
                )
            preferLowLatencyAudio = lowLatencyAudio && !systemCoreConfig.forceStandardAudioBuffer
            rumbleEventsEnabled = requestRumble
            skipDuplicateFrames = systemCoreConfig.skipDuplicateFrames
            allowFrameCatchUp = systemCoreConfig.allowFrameCatchUp
            nonBlockingVulkanPresent = systemCoreConfig.nonBlockingVulkanPresent
            asyncVulkanPresentation = systemCoreConfig.asyncVulkanPresentation
            enableMicrophone = requestMicrophone
            immersiveMode = buildImmersiveModeConfiguration(enableImmersiveMode)
        }
    }

    private fun buildImmersiveModeConfiguration(enableImmersiveMode: Boolean): ImmersiveMode? {
        return if (enableImmersiveMode) {
            ImmersiveMode(blendFactor = 0.05f)
        } else {
            null
        }
    }

    private fun getLoadingMessage(loadingState: GameLoader.LoadingState): String {
        return when (loadingState) {
            is GameLoader.LoadingState.LoadingCore -> {
                appContext.getString(com.omnidroid.ext.R.string.game_loading_download_core)
            }

            is GameLoader.LoadingState.LoadingGame -> {
                appContext.getString(R.string.game_loading_preparing_game)
            }

            is GameLoader.LoadingState.ExtractingGame -> {
                "${appContext.getString(R.string.game_loading_preparing_game)} (${loadingState.percent}%)"
            }

            else -> ""
        }
    }

    private fun printRetroVariables(retroGameView: IRetroGameView) {
        scope.launch {
            // Some cores do not immediately call SET_VARIABLES so we might need to wait a little bit
            delay(1.seconds)
            retroGameView.getVariables()?.forEach {
                Timber.i("Libretro variable: $it")
            }
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        owner.launchOnState(Lifecycle.State.STARTED) {
            initializeRetroGameViewErrorsFlow()
        }

        owner.launchOnState(Lifecycle.State.RESUMED) {
            initializeCoreVariablesFlow()
        }

        owner.launchOnState(Lifecycle.State.RESUMED) {
            initializeRumbleFlow()
        }
    }

    private suspend fun initializeCoreVariablesFlow() {
        try {
            waitRetroGameViewInitialized()
            val options = coreVariablesManager.getOptionsForCore(system.id, systemCoreConfig)
            updateCoreVariables(options)
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private suspend fun initializeRumbleFlow() {
        val retroGameView = retroGameViewFlow()
        val rumbleEvents = retroGameView.getRumbleEvents()
        rumbleManager.collectAndProcessRumbleEvents(systemCoreConfig, rumbleEvents)
    }

    private suspend fun initializeRetroGameViewErrorsFlow() {
        retroGameViewFlow().getGLRetroErrors()
            .catch { Timber.e(it, "Exception in GLRetroErrors. Ironic.") }
            .collect { handleRetroViewError(it) }
    }

    private fun updateCoreVariables(options: List<CoreVariable>) {
        val update = rendererSettingsGuard?.filter(options)
        if (update?.notifyDeferredChange == true) {
            sideEffects.showToast(appContext.getString(R.string.game_toast_renderer_change_deferred))
        }
        val validator = raCoreOptionValidator
        val candidateVariables = update?.applicable ?: options
        val filteredVariables =
            if (validator != null) {
                candidateVariables.filter { opt ->
                    val ok = validator(opt.key, opt.value)
                    if (!ok) {
                        sideEffects.showToast(
                            appContext.getString(R.string.ra_hardcore_blocked_core_option, opt.key, opt.value),
                        )
                    }
                    ok
                }
            } else {
                candidateVariables
            }
        val updatedVariables =
            filteredVariables
                .map { Variable(it.key, it.value) }
                .toTypedArray()

        updatedVariables.forEach {
            Timber.i("Updating core variable: ${it.key} ${it.value}")
        }

        retroGameView?.updateVariables(*updatedVariables)
    }

    fun applyCoreVariables(options: List<CoreVariable>) {
        if (options.isEmpty()) return
        updateCoreVariables(options)
    }

    fun refreshCoreVariables() {
        scope.launch {
            try {
                val options = coreVariablesManager.getOptionsForCore(system.id, systemCoreConfig)
                updateCoreVariables(options)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    private fun handleRetroViewError(errorCode: Int) {
        Timber.e("Error in GLRetroView $errorCode")
        val gameLoaderError =
            when (errorCode) {
                GLRetroView.ERROR_GL_NOT_COMPATIBLE -> GameLoaderError.GLIncompatible
                GLRetroView.ERROR_LOAD_GAME -> GameLoaderError.LoadGame
                GLRetroView.ERROR_LOAD_LIBRARY -> GameLoaderError.LoadCore
                GLRetroView.ERROR_SERIALIZATION -> GameLoaderError.Saves
                else -> GameLoaderError.Generic
            }

        scope.launch {
            if ((gameLoaderError is GameLoaderError.LoadGame || gameLoaderError is GameLoaderError.LoadCore) &&
                CoreAssets.hasAssets(systemCoreConfig.coreID) && !CoreAssets.isReady(appContext, systemCoreConfig.coreID)
            ) {
                sideEffects.requestConsoleSetup(getErrorMessage(GameLoaderError.MissingAssets(systemCoreConfig.coreID)))
            } else {
                sideEffects.requestFailureFinish(getErrorMessage(gameLoaderError))
            }
        }
    }

    private fun getErrorMessage(gameError: GameLoaderError): String {
        val message =
            when (gameError) {
                is GameLoaderError.MissingAssets -> appContext.getString(
                    R.string.game_missing_assets_message,
                    appContext.getString(system.metaSystemID().titleResId),
                )
                is GameLoaderError.CoreUninstalled -> appContext.getString(R.string.game_core_uninstalled_message)
                is GameLoaderError.GLIncompatible -> {
                    appContext.getString(R.string.game_loader_error_gl_incompatible)
                }
                is GameLoaderError.Generic -> {
                    appContext.getString(R.string.game_loader_error_generic)
                }
                is GameLoaderError.LoadCore -> {
                    appContext.getString(com.omnidroid.ext.R.string.game_loader_error_load_core)
                }
                is GameLoaderError.LoadGame -> {
                    appContext.getString(R.string.game_loader_error_load_game)
                }
                is GameLoaderError.Saves -> {
                    appContext.getString(R.string.game_loader_error_save)
                }
                is GameLoaderError.UnsupportedArchitecture -> {
                    appContext.getString(R.string.game_loader_error_unsupported_architecture)
                }
                is GameLoaderError.MissingBiosFiles -> {
                    appContext.getString(R.string.game_loader_error_missing_bios, gameError.missingFiles)
                }
                is GameLoaderError.ArchiveError -> {
                    gameError.reason
                }
            }

        return message
    }
}
