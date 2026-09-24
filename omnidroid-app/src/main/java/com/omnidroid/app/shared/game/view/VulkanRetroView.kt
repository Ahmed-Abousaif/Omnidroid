package com.omnidroid.app.shared.game.view

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.swordfish.libretrodroid.Controller
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.libretrodroid.RumbleEvent
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.Variable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SurfaceView-based hardware rendering view for Vulkan-accelerated Libretro cores.
 */
class VulkanRetroView(
    context: Context,
    private val data: GLRetroViewData,
) : SurfaceView(context), SurfaceHolder.Callback, IRetroGameView {

    private val retroEventsSubject = MutableSharedFlow<GLRetroView.GLRetroEvents>(extraBufferCapacity = 64)
    private val errorsSubject = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    private val rumbleEventsSubject = MutableSharedFlow<RumbleEvent>(extraBufferCapacity = 64)

    private var lifecycle: Lifecycle? = null
    private var emulationThread: EmulationThread? = null

    private val isEmulationReady = AtomicBoolean(false)
    private val isGameLoaded = AtomicBoolean(false)
    private var isTouchDisabled = false

    override var audioEnabled: Boolean = true
        set(value) {
            field = value
            runCatching { LibretroDroid.setAudioEnabled(value) }
        }

    override var frameSpeed: Int = 1
        set(value) {
            field = value
            runCatching { LibretroDroid.setFrameSpeed(value) }
        }

    override var shader: ShaderConfig? = null
        set(value) {
            field = value
            if (value != null) {
                runCatching {
                    LibretroDroidBridge.setShaderConfig(value)
                }
            }
        }

    override var viewport: RectF? = null
        set(value) {
            field = value
            if (value != null) {
                runCatching {
                    LibretroDroid.setViewport(value.left, value.top, value.width(), value.height())
                }
            }
        }

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun asView(): View = this

    override fun surfaceCreated(holder: SurfaceHolder) {
        Timber.i("VulkanRetroView: surfaceCreated")
        LibretroDroid.setSurface(holder.surface)
        startEmulationThread(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Timber.i("VulkanRetroView: surfaceChanged: ${width}x$height")
        emulationThread?.onSurfaceChanged(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Timber.i("VulkanRetroView: surfaceDestroyed")
        stopEmulationThread()
        LibretroDroid.onSurfaceDestroyed()
    }

    private fun startEmulationThread(surface: Surface) {
        stopEmulationThread()
        val thread = EmulationThread(surface)
        emulationThread = thread
        thread.start()
    }

    private fun stopEmulationThread() {
        emulationThread?.let {
            it.requestStop()
            try {
                it.join(1000)
            } catch (e: InterruptedException) {
                Timber.w(e, "Interrupted waiting for emulation thread")
            }
        }
        emulationThread = null
    }

    override fun onCreate(owner: LifecycleOwner) {
        this.lifecycle = owner.lifecycle
        owner.lifecycle.addObserver(this)

        val refreshRate = context.display?.refreshRate ?: 60f
        val language = Locale.getDefault().language

        runCatching {
            LibretroDroidBridge.create(
                LibretroDroid.GLES_VERSION_VULKAN,
                data.coreFilePath,
                data.systemDirectory,
                data.savesDirectory,
                data.variables,
                data.shader,
                refreshRate,
                data.preferLowLatencyAudio,
                data.gameVirtualFiles.isNotEmpty(),
                data.enableMicrophone,
                data.skipDuplicateFrames,
                data.immersiveMode,
                language,
            )
            data.rumbleEventsEnabled.let { LibretroDroid.setRumbleEnabled(it) }
            LibretroDroid.setViewportAlignment(data.viewportAlignment.value)
        }.onFailure { t ->
            Timber.e(t, "Failed to create LibretroDroid in onCreate")
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        if (isGameLoaded.get()) {
            emulationThread?.resumeEmulation()
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        if (isGameLoaded.get()) {
            emulationThread?.pauseEmulation()
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        stopEmulationThread()
        if (isGameLoaded.get()) {
            runCatching { LibretroDroid.destroy() }
        }
    }

    override fun sendKeyEvent(action: Int, keyCode: Int, port: Int) {
        runCatching { LibretroDroid.onKeyEvent(port, action, keyCode) }
    }

    override fun sendMotionEvent(source: Int, x: Float, y: Float, port: Int) {
        runCatching { LibretroDroid.onMotionEvent(port, source, x, y) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isTouchDisabled) return false
        val point = normalizeTouchCoordinates(event.x, event.y)
        runCatching { LibretroDroid.onTouchEvent(point.x, point.y) }
        return true
    }

    private fun normalizeTouchCoordinates(rawX: Float, rawY: Float): PointF {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val normX = (2f * rawX / w - 1f).coerceIn(-1f, 1f)
        val normY = (2f * rawY / h - 1f).coerceIn(-1f, 1f)
        return PointF(normX, normY)
    }

    override fun serializeState(useEmulationThread: Boolean): ByteArray? {
        return runOnEmulationThread(useEmulationThread) {
            LibretroDroid.serializeState()
        }
    }

    override fun unserializeState(state: ByteArray, useEmulationThread: Boolean): Boolean {
        return runOnEmulationThread(useEmulationThread) {
            LibretroDroid.unserializeState(state)
        } ?: false
    }

    override fun serializeSRAM(useEmulationThread: Boolean): ByteArray? {
        return runOnEmulationThread(useEmulationThread) {
            LibretroDroid.serializeSRAM()
        }
    }

    override fun unserializeSRAM(state: ByteArray, useEmulationThread: Boolean): Boolean {
        return runOnEmulationThread(useEmulationThread) {
            LibretroDroid.unserializeSRAM(state)
        } ?: false
    }

    override fun reset(useEmulationThread: Boolean) {
        runOnEmulationThread(useEmulationThread) {
            LibretroDroid.reset()
        }
    }

    override fun setCheat(index: Int, enabled: Boolean, code: String, useEmulationThread: Boolean) {
        runOnEmulationThread(useEmulationThread) {
            LibretroDroid.setCheat(index, enabled, code)
        }
    }

    override fun getVariables(): Array<Variable>? {
        return runCatching { LibretroDroid.getVariables() }.getOrNull()
    }

    override fun updateVariables(vararg variables: Variable) {
        for (variable in variables) {
            runCatching { LibretroDroid.updateVariable(variable) }
        }
    }

    override fun getAvailableDisks(useEmulationThread: Boolean): Int {
        return runOnEmulationThread(useEmulationThread) {
            LibretroDroid.availableDisks()
        } ?: 0
    }

    override fun getCurrentDisk(useEmulationThread: Boolean): Int {
        return runOnEmulationThread(useEmulationThread) {
            LibretroDroid.currentDisk()
        } ?: 0
    }

    override fun changeDisk(index: Int, useEmulationThread: Boolean) {
        runOnEmulationThread(useEmulationThread) {
            LibretroDroid.changeDisk(index)
        }
    }

    override fun setControllerType(port: Int, controllerId: Int) {
        runCatching { LibretroDroid.setControllerType(port, controllerId) }
    }

    override fun getControllers(): Array<Array<Controller>>? {
        return runCatching { LibretroDroid.getControllers() }.getOrNull()
    }

    override fun getGLRetroEvents(): Flow<GLRetroView.GLRetroEvents> = retroEventsSubject

    override fun getGLRetroErrors(): Flow<Int> = errorsSubject

    override fun getRumbleEvents(): Flow<RumbleEvent> = rumbleEventsSubject

    override fun disableTouchEvents() {
        isTouchDisabled = true
    }

    override fun refreshAspectRatio() {
        runCatching { LibretroDroid.refreshAspectRatio() }
    }

    private fun <T> runOnEmulationThread(useEmulationThread: Boolean, action: () -> T): T? {
        val thread = emulationThread
        if (!useEmulationThread || thread == null || Thread.currentThread() == thread) {
            return action()
        }
        var result: T? = null
        val latch = CountDownLatch(1)
        thread.postAction {
            try {
                result = action()
            } finally {
                latch.countDown()
            }
        }
        try {
            latch.await()
        } catch (e: InterruptedException) {
            Timber.w(e, "Interrupted waiting for action on emulation thread")
        }
        return result
    }

    /**
     * Dedicated native emulation and frame pump thread for Vulkan.
     */
    private inner class EmulationThread(
        private val surface: Surface,
    ) : Thread("VulkanEmulationThread") {

        private val isRunning = AtomicBoolean(true)
        private val isPaused = AtomicBoolean(false)
        private val pendingActions = java.util.concurrent.ConcurrentLinkedQueue<() -> Unit>()

        fun requestStop() {
            isRunning.set(false)
            interrupt()
        }

        fun pauseEmulation() {
            isPaused.set(true)
            runCatching { LibretroDroid.pause() }
        }

        fun resumeEmulation() {
            isPaused.set(false)
            runCatching { LibretroDroid.resume() }
        }

        fun onSurfaceChanged(width: Int, height: Int) {
            postAction {
                runCatching { LibretroDroid.onSurfaceChanged(width, height) }
            }
        }

        fun postAction(action: () -> Unit) {
            pendingActions.add(action)
        }

        override fun run() {
            Timber.i("VulkanEmulationThread: starting")

            // Initialize core and load game
            try {
                priority = MAX_PRIORITY
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)

                LibretroDroid.setSurface(surface)

                // Load game
                val gameFilePath = data.gameFilePath
                val gameVirtualFiles = data.gameVirtualFiles
                val gameFileBytes = data.gameFileBytes

                when {
                    gameFilePath != null -> LibretroDroid.loadGameFromPath(gameFilePath)
                    gameVirtualFiles.isNotEmpty() -> LibretroDroidBridge.loadGameFromVirtualFiles(gameVirtualFiles)
                    gameFileBytes != null -> LibretroDroid.loadGameFromBytes(gameFileBytes)
                }

                // Restore SRAM if present
                data.saveRAMState?.let { sram ->
                    LibretroDroid.unserializeSRAM(sram)
                }

                LibretroDroid.onSurfaceCreated()
                LibretroDroid.resume()
                isGameLoaded.set(true)
                isEmulationReady.set(true)
                retroEventsSubject.tryEmit(GLRetroView.GLRetroEvents.SurfaceCreated)
            } catch (t: Throwable) {
                Timber.e(t, "Failed to initialize LibretroDroid for Vulkan")
                errorsSubject.tryEmit(LibretroDroid.ERROR_LOAD_GAME)
                return
            }

            // Emulation render loop
            while (isRunning.get()) {
                // Execute queued actions
                while (true) {
                    val action = pendingActions.poll() ?: break
                    runCatching { action() }
                }

                if (!isPaused.get()) {
                    try {
                        LibretroDroid.step(null)
                        retroEventsSubject.tryEmit(GLRetroView.GLRetroEvents.FrameRendered)
                    } catch (t: Throwable) {
                        Timber.e(t, "Error in Vulkan LibretroDroid step")
                    }
                } else {
                    try {
                        sleep(10)
                    } catch (e: InterruptedException) {
                        if (!isRunning.get()) break
                    }
                }
            }

            Timber.i("VulkanEmulationThread: exited")
        }
    }
}
