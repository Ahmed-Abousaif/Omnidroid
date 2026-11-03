package com.omnidroid.app.shared.switch_emu

import android.content.Context
import android.net.Uri
import android.view.MotionEvent
import android.view.Surface
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.features.input.NativeInput
import org.yuzu.yuzu_emu.features.input.model.NativeAnalog
import org.yuzu.yuzu_emu.features.input.model.NativeButton
import org.yuzu.yuzu_emu.features.input.model.NpadStyleIndex
import org.yuzu.yuzu_emu.utils.FileUtil
import org.yuzu.yuzu_emu.utils.NativeConfig
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

object EdenBridge {

    private val isInitialized = AtomicBoolean(false)
    private var emulationThread: Thread? = null
    @Volatile
    private var cachedSwitchDir: File? = null
    @Volatile
    private var activePort: Int = NativeInput.ConsoleDevice
    @Volatile
    private var isDockedMode: Boolean = false
    @Volatile
    private var isAsyncShaders: Boolean = true

    fun getSwitchBaseDir(context: Context): File {
        val dir = File(context.filesDir, "switch")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getKeysDir(context: Context): File {
        val dir = File(getSwitchBaseDir(context), "keys")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getNandDir(context: Context): File {
        val dir = File(getSwitchBaseDir(context), "nand")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getFirmwareDir(context: Context): File {
        val dir = File(getNandDir(context), "system/Contents/registered")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun isFirmwareInstalled(context: Context): Boolean {
        val fwDir = File(getNandDir(context), "system/Contents/registered")
        if (!fwDir.exists() || !fwDir.isDirectory) return false
        val ncas = fwDir.listFiles { _, name -> name.endsWith(".nca", ignoreCase = true) }
        return !ncas.isNullOrEmpty()
    }

    fun getInstalledFirmwareVersion(context: Context): String? {
        if (!isFirmwareInstalled(context)) return null
        if (NativeLibrary.isLoaded) {
            val ver = runCatching { NativeLibrary.firmwareVersion() }.getOrNull()
            if (!ver.isNullOrBlank() && ver != "N/A") {
                return ver
            }
        }
        val count = getFirmwareDir(context).listFiles { _, name -> name.endsWith(".nca", ignoreCase = true) }?.size ?: 0
        return if (count > 0) "$count NCAs" else null
    }

    fun importFirmware(context: Context, inputStream: InputStream): Pair<Boolean, String> {
        val tempDir = File(context.cacheDir, "fw_temp_" + System.currentTimeMillis()).apply { mkdirs() }
        return try {
            var ncaCount = 0
            java.util.zip.ZipInputStream(java.io.BufferedInputStream(inputStream)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".nca", ignoreCase = true)) {
                        val fileName = File(entry.name).name
                        val target = File(tempDir, fileName)
                        FileOutputStream(target).use { fos ->
                            zis.copyTo(fos)
                        }
                        ncaCount++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            if (ncaCount == 0) {
                tempDir.deleteRecursively()
                return Pair(false, "No .nca files found in archive")
            }
            val destDir = getFirmwareDir(context)
            destDir.deleteRecursively()
            destDir.mkdirs()
            tempDir.copyRecursively(destDir, overwrite = true)
            tempDir.deleteRecursively()
            if (NativeLibrary.isLoaded) {
                runCatching { NativeLibrary.initializeSystem(true) }
            }
            val ver = getInstalledFirmwareVersion(context) ?: "$ncaCount files"
            Timber.i("Successfully installed Switch firmware ($ncaCount files, $ver)")
            Pair(true, ver)
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            Timber.e(e, "Error importing firmware archive")
            Pair(false, e.message ?: "Failed to install firmware")
        }
    }

    fun areKeysPresent(context: Context): Boolean {
        val keysDir = getKeysDir(context)
        val prodKeys = File(keysDir, "prod.keys")
        if (prodKeys.exists() && prodKeys.length() > 0) {
            return true
        }
        return false
    }

    fun importProdKeys(context: Context, inputStream: InputStream): Boolean {
        return try {
            val prodKeys = File(getKeysDir(context), "prod.keys")
            FileOutputStream(prodKeys).use { out ->
                inputStream.copyTo(out)
            }
            if (NativeLibrary.isLoaded) {
                NativeLibrary.reloadKeys()
            }
            Timber.i("Successfully imported prod.keys (${prodKeys.length()} bytes)")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to import prod.keys")
            false
        }
    }

    fun initialize(context: Context) {
        if (isInitialized.compareAndSet(false, true)) {
            FileUtil.init(context)
            try {
                NativeLibrary.load()
            } catch (e: Throwable) {
                Timber.e(e, "Native library yuzu-android could not be loaded")
                return
            }

            val switchDir = getSwitchBaseDir(context).also { cachedSwitchDir = it }
            File(switchDir, "nand").mkdirs()
            File(switchDir, "load").mkdirs()
            File(switchDir, "config").mkdirs()
            File(switchDir, "cache").mkdirs()
            File(switchDir, "keys").mkdirs()

            val hookLibPath = context.applicationInfo.nativeLibraryDir + "/"
            val redirectDir = File(switchDir, "gpu_redirect").apply { mkdirs() }.canonicalPath + "/"
            try {
                Timber.i("Initializing GPU driver with hookLibPath: $hookLibPath")
                NativeLibrary.initializeGpuDriver(
                    hookLibPath,
                    "",
                    "",
                    redirectDir,
                )
            } catch (e: Throwable) {
                Timber.e(e, "Error initializing GPU driver")
            }

            NativeLibrary.setAppDirectory(switchDir.absolutePath)
            val configFile = File(switchDir, "config/config.ini")
            try {
                if (configFile.exists()) {
                    patchConfigFile(configFile, false)
                }
                NativeConfig.initializeGlobalConfig()
                NativeLibrary.initializeSystem(false)
                applyOptimizedSettings()
                NativeConfig.saveGlobalConfig()
                patchConfigFile(configFile, false)
            } catch (e: Throwable) {
                Timber.w(e, "NativeConfig.initializeGlobalConfig() failed or not implemented")
            }

            NativeLibrary.reloadKeys()
        }
    }

    fun ensureGpuDriver(context: Context) {
        val switchDir = getSwitchBaseDir(context)
        val hookLibPath = context.applicationInfo.nativeLibraryDir + "/"
        val redirectDir = File(switchDir, "gpu_redirect").apply { mkdirs() }.canonicalPath + "/"
        try {
            Timber.i("Ensuring GPU driver with hookLibPath: $hookLibPath")
            NativeLibrary.initializeGpuDriver(
                hookLibPath,
                "",
                "",
                redirectDir,
            )
        } catch (e: Throwable) {
            Timber.e(e, "Error ensuring GPU driver")
        }
    }

    fun setAudioMuted(muted: Boolean) {
        if (NativeLibrary.isLoaded) {
            try {
                NativeConfig.setBoolean("audio_muted", muted)
                if (NativeLibrary.isRunning()) {
                    NativeLibrary.applySettings()
                }
                Timber.i("Eden audio_muted set to $muted")
            } catch (e: Throwable) {
                Timber.w(e, "Could not set audio_muted to $muted")
            }
        }
    }

    fun setDockedMode(docked: Boolean) {
        isDockedMode = docked
        if (NativeLibrary.isLoaded) {
            try {
                NativeConfig.setBoolean("use_docked_mode", docked)
                if (NativeLibrary.isRunning()) {
                    NativeLibrary.applySettings()
                }
                configureControllers(docked)
                val switchDir = cachedSwitchDir
                val configFile = if (switchDir != null) File(switchDir, "config/config.ini") else null
                if (configFile != null && configFile.exists()) {
                    patchConfigFile(configFile, docked)
                }
                Timber.i("Eden use_docked_mode set to $docked")
            } catch (e: Throwable) {
                Timber.w(e, "Could not set use_docked_mode to $docked")
            }
        }
    }

    fun setAsyncShaders(enabled: Boolean) {
        isAsyncShaders = enabled
        if (NativeLibrary.isLoaded) {
            try {
                NativeConfig.setBoolean("use_asynchronous_shaders", enabled)
                if (NativeLibrary.isRunning()) {
                    NativeLibrary.applySettings()
                }
                val switchDir = cachedSwitchDir
                val configFile = if (switchDir != null) File(switchDir, "config/config.ini") else null
                if (configFile != null && configFile.exists()) {
                    patchConfigFile(configFile, isDockedMode)
                }
                Timber.i("Eden use_asynchronous_shaders set to $enabled")
            } catch (e: Throwable) {
                Timber.w(e, "Could not set use_asynchronous_shaders to $enabled")
            }
        }
    }

    fun configureControllers(docked: Boolean) {
        if (!NativeLibrary.isLoaded) return
        try {
            if (docked) {
                NativeInput.setStyleIndex(0, NpadStyleIndex.Fullkey)
                NativeInput.connectControllers(0, true)
                activePort = NativeInput.Player1Device
            } else {
                NativeInput.setStyleIndex(0, NpadStyleIndex.Handheld)
                NativeInput.connectControllers(0, true)
                activePort = NativeInput.ConsoleDevice
            }
            Timber.i(
                "EdenBridge.configureControllers: docked=%b, style=%s, isConnected0=%b, activePort=%d",
                docked,
                if (docked) "Fullkey" else "Handheld",
                NativeInput.getIsConnected(0),
                activePort
            )
        } catch (e: Throwable) {
            Timber.e(e, "Error configuring Switch controllers")
        }
    }

    fun setResolutionSetup(resolutionSetup: Int) {
        if (NativeLibrary.isLoaded) {
            try {
                NativeConfig.setInt("resolution_setup", resolutionSetup)
                if (NativeLibrary.isRunning()) {
                    NativeLibrary.applySettings()
                }
                Timber.i("Eden resolution_setup set to $resolutionSetup")
            } catch (e: Throwable) {
                Timber.w(e, "Could not set resolution_setup to $resolutionSetup")
            }
        }
    }

    private fun getRendererOverrides(): Map<String, String> = mapOf(
        "use_asynchronous_shaders" to if (isAsyncShaders) "true" else "false",
        "use_asynchronous_gpu_emulation" to "false",
        "async_presentation" to "false",
        "use_reactive_flushing" to "false",
        "barrier_feedback_loops" to "true",
        "nvdec_emulation" to "2",
        "astc_recompression" to "0",
        "max_anisotropy" to "0",
        "gpu_accuracy" to "0",
        "use_disk_shader_cache" to "true",
        "use_vulkan_driver_pipeline_cache" to "true",
    )

    private fun applyOptimizedSettings() {
        try {
            NativeConfig.setBoolean("use_asynchronous_shaders", isAsyncShaders)
            NativeConfig.setBoolean("use_asynchronous_gpu_emulation", false)
            NativeConfig.setBoolean("async_presentation", false)
            NativeConfig.setBoolean("use_reactive_flushing", false)
            NativeConfig.setBoolean("barrier_feedback_loops", true)
            NativeConfig.setBoolean("use_disk_shader_cache", true)
            NativeConfig.setBoolean("use_vulkan_driver_pipeline_cache", true)
            NativeConfig.setInt("nvdec_emulation", 2) // 2 = GPU
            NativeConfig.setInt("astc_recompression", 0) // 0 = Uncompressed
            NativeConfig.setInt("max_anisotropy", 0) // 0 = Automatic
            NativeConfig.setInt("cpu_backend", 1) // 1 = NCE
            NativeConfig.setInt("gpu_accuracy", 0) // 0 = Normal
            NativeConfig.setInt("pipeline_worker_count", 4)
        } catch (e: Throwable) {
            Timber.w(e, "Failed to apply optimized NativeConfig settings")
        }
    }

    fun patchConfigFile(configFile: File, docked: Boolean) {
        if (!configFile.exists()) return
        try {
            val lines = configFile.readLines()
            val newLines = mutableListOf<String>()
            var inControls = false
            var inRenderer = false
            val appliedRendererKeys = mutableSetOf<String>()

            fun flushMissingRendererSettings() {
                for ((key, value) in getRendererOverrides()) {
                    if (key !in appliedRendererKeys) {
                        newLines.add("$key=$value")
                        newLines.add("$key\\default=false")
                        appliedRendererKeys.add(key)
                    }
                }
            }

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    if (inRenderer) {
                        flushMissingRendererSettings()
                    }
                    inControls = trimmed.equals("[Controls]", ignoreCase = true)
                    inRenderer = trimmed.equals("[Renderer]", ignoreCase = true)
                }
                if (inControls) {
                    when {
                        trimmed.startsWith("player_0_type=") -> {
                            newLines.add("player_0_type=${if (docked) 0 else 4}")
                            continue
                        }
                        trimmed.startsWith("player_0_type\\default=") -> {
                            newLines.add("player_0_type\\default=false")
                            continue
                        }
                        trimmed.startsWith("player_0_connected=") -> {
                            newLines.add("player_0_connected=true")
                            continue
                        }
                        trimmed.startsWith("player_0_connected\\default=") -> {
                            newLines.add("player_0_connected\\default=false")
                            continue
                        }
                        trimmed.startsWith("player_8_type=") -> {
                            newLines.add("player_8_type=${if (docked) 0 else 4}")
                            continue
                        }
                        trimmed.startsWith("player_8_type\\default=") -> {
                            newLines.add("player_8_type\\default=false")
                            continue
                        }
                        trimmed.startsWith("player_8_connected=") -> {
                            newLines.add("player_8_connected=${if (docked) "false" else "true"}")
                            continue
                        }
                        trimmed.startsWith("player_8_connected\\default=") -> {
                            newLines.add("player_8_connected\\default=false")
                            continue
                        }
                    }
                }
                if (inRenderer) {
                    var handled = false
                    for ((key, value) in getRendererOverrides()) {
                        if (trimmed.startsWith("$key=")) {
                            newLines.add("$key=$value")
                            appliedRendererKeys.add(key)
                            handled = true
                            break
                        } else if (trimmed.startsWith("$key\\default=")) {
                            newLines.add("$key\\default=false")
                            handled = true
                            break
                        }
                    }
                    if (handled) continue
                }
                newLines.add(line)
            }
            if (inRenderer) {
                flushMissingRendererSettings()
            }
            configFile.writeText(newLines.joinToString("\n"))
            Timber.i("EdenBridge: Patched config.ini successfully for docked=$docked")
        } catch (e: Exception) {
            Timber.w(e, "EdenBridge: Could not patch config.ini")
        }
    }

    fun startEmulation(
        gamePath: String,
        surface: Surface,
        docked: Boolean,
        resolutionSetup: Int = 3,
        audioMuted: Boolean = false,
        asyncShaders: Boolean = true,
        callback: NativeLibrary.EmulationCallback,
    ) {
        isDockedMode = docked
        isAsyncShaders = asyncShaders
        NativeLibrary.emulationCallback = callback
        // NOTE: DO NOT call NativeLibrary.initializeSystem(false) here!
        // initializeSystem(false) was already called once during initialize().
        // Calling it a second time re-instantiates InputSubsystem::virtual_gamepad
        // while Common::Input retains the first instance, breaking all input dispatch.
        NativeLibrary.surfaceChanged(surface)

        val switchDir = cachedSwitchDir
        val configFile = if (switchDir != null) File(switchDir, "config/config.ini") else null

        try {
            NativeConfig.setBoolean("use_docked_mode", docked)
            NativeConfig.setInt("resolution_setup", resolutionSetup)
            NativeConfig.setBoolean("audio_muted", audioMuted)
            NativeConfig.setBoolean("use_asynchronous_shaders", asyncShaders)
            applyOptimizedSettings()
            NativeConfig.saveGlobalConfig()
            if (configFile != null) {
                patchConfigFile(configFile, docked)
            }
        } catch (e: Throwable) {
            Timber.w(e, "Could not apply initial emulation settings")
        }

        configureControllers(docked)

        emulationThread = Thread({
            try {
                Timber.i("Starting Eden emulation for path: $gamePath")
                NativeLibrary.run(gamePath, 0, true)
                Timber.i("Eden emulation loop terminated normally")
            } catch (e: Throwable) {
                Timber.e(e, "Exception in Eden emulation loop")
                callback.onExitEmulation(-1)
            }
        }, "EdenEmulationThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun pause() {
        if (NativeLibrary.isLoaded && NativeLibrary.isRunning()) {
            NativeLibrary.pauseEmulation()
        }
    }

    fun resume() {
        if (NativeLibrary.isLoaded && NativeLibrary.isPaused()) {
            NativeLibrary.unpauseEmulation()
        }
    }

    fun stop() {
        if (NativeLibrary.isLoaded) {
            try {
                NativeLibrary.stopEmulation()
            } catch (e: Throwable) {
                Timber.e(e, "Error stopping Eden emulation")
            }
            val threadToJoin = emulationThread
            emulationThread = null
            Thread({
                try {
                    threadToJoin?.join(2000)
                } catch (e: InterruptedException) {
                    Timber.w(e, "Interrupted while waiting for Eden emulation thread")
                }
            }, "EdenThreadReaper").start()
        }
    }

    fun onSurfaceChanged(surface: Surface) {
        if (NativeLibrary.isLoaded) {
            NativeLibrary.surfaceChanged(surface)
        }
    }

    fun onSurfaceDestroyed() {
        if (NativeLibrary.isLoaded) {
            NativeLibrary.surfaceDestroyed()
        }
    }

    fun sendOverlayButton(button: NativeButton, pressed: Boolean) {
        if (NativeLibrary.isLoaded) {
            val action = if (pressed) NativeInput.ButtonState.PRESSED else NativeInput.ButtonState.RELEASED
            NativeInput.onOverlayButtonEvent(activePort, button, action)
        }
    }

    fun sendOverlayJoystick(stick: NativeAnalog, x: Float, y: Float) {
        if (NativeLibrary.isLoaded) {
            NativeInput.onOverlayJoystickEvent(activePort, stick, x, y)
        }
    }

    fun sendTouch(fingerId: Int, x: Float, y: Float, action: Int) {
        if (!NativeLibrary.isLoaded) return
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                NativeInput.onTouchPressed(fingerId, x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                NativeInput.onTouchMoved(fingerId, x, y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                NativeInput.onTouchReleased(fingerId)
            }
        }
    }
}

