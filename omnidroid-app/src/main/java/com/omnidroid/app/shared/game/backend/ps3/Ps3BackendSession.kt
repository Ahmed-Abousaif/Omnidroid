package com.omnidroid.app.shared.game.backend.ps3

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import com.omnidroid.app.shared.game.view.IRetroGameView
import com.omnidroid.app.shared.ps3.Armsx3Bridge
import com.omnidroid.app.shared.ps3.Ps3PadState
import com.omnidroid.lib.backend.*
import com.omnidroid.lib.core.CoreVariablesManager
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.libretrodroid.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class Ps3BackendSession(
    private val activity: Activity,
    private val game: Game,
    private val preferences: SharedPreferences,
    override val capabilities: BackendCapabilities,
    private val savesManager: com.omnidroid.lib.saves.SavesManager,
    private val onExit: (Int) -> Unit,
) : BackendSession, NativeSaveSession, NativeStateSession, ScreenshotSession, IRetroGameView, SurfaceHolder.Callback {
    private val container = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }
    private val surfaceView = SurfaceView(activity).apply {
        holder.setFormat(PixelFormat.RGBX_8888)
        holder.setKeepScreenOn(true)
        holder.addCallback(this@Ps3BackendSession)
    }
    private val events = MutableSharedFlow<GLRetroView.GLRetroEvents>(replay = 1)
    private val pads = Array(7) { Ps3PadState() }
    private val activePorts = mutableSetOf<Int>()
    private val stopped = AtomicBoolean(false)
    private val guestDialogs = com.omnidroid.app.shared.ps3.Ps3GuestDialogs.attach(activity)
    private var bootQueued = false
    private var restarting = false
    @Volatile private var surfaceReady = false
    @Volatile private var paused = false
    @Volatile private var muted = false
    private val displayManager = activity.getSystemService(android.hardware.display.DisplayManager::class.java)
    private var displayListenerRegistered = false
    private val displayListener = object : android.hardware.display.DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) {}
        override fun onDisplayRemoved(id: Int) {}
        override fun onDisplayChanged(id: Int) {
            if (surfaceView.display?.displayId == id) updateDisplayRate()
        }
    }
    private fun updateDisplayRate() {
        val hz = (surfaceView.display ?: activity.display)?.refreshRate ?: return
        Armsx3Bridge.displayRate(hz)
    }
    private fun releaseDisplayListener() {
        if (displayListenerRegistered) displayManager?.unregisterDisplayListener(displayListener)
        displayListenerRegistered = false
    }
    private val titleId = com.omnidroid.lib.library.scan.Ps3Content.read(activity, android.net.Uri.parse(game.fileUri))?.titleId

    private suspend fun transferState(save: Boolean, index: Int) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val slots = com.omnidroid.lib.saves.Ps3StateSlots.forTitle(activity, requireNotNull(titleId))
        Armsx3Bridge.executor.submit<Boolean> {
            try { Armsx3Bridge.stateSlot(save, index, slots) }
            finally { if (paused) Armsx3Bridge.pause() else if (surfaceReady && !stopped.get()) Armsx3Bridge.resume() }
        }.get()
    }

    override suspend fun saveNativeState(index: Int) = transferState(true, index)
    override suspend fun loadNativeState(index: Int) = transferState(false, index)

    override suspend fun captureScreenshot(): android.graphics.Bitmap? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        if (!surfaceReady || surfaceView.width <= 1) return@withContext null
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val bitmap = android.graphics.Bitmap.createBitmap(surfaceView.width, surfaceView.height, android.graphics.Bitmap.Config.ARGB_8888)
            try {
                android.view.PixelCopy.request(surfaceView, bitmap, { result ->
                    if (result == android.view.PixelCopy.SUCCESS && continuation.isActive) continuation.resumeWith(Result.success(bitmap))
                    else {
                        bitmap.recycle()
                        if (continuation.isActive) continuation.resumeWith(Result.success(null))
                    }
                }, android.os.Handler(android.os.Looper.getMainLooper()))
            } catch (failure: IllegalArgumentException) {
                bitmap.recycle()
                continuation.resumeWith(Result.success(null))
            }
        }
    }

    override suspend fun persistNativeSaves(stop: Boolean) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val id = titleId ?: return@withContext
        if (stop) {
            stopped.set(true)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { guestDialogs.detach(); releaseDisplayListener() }
        }
        val data = Armsx3Bridge.executor.submit<ByteArray?> {
            if (stop) Armsx3Bridge.stop() else Armsx3Bridge.pause()
            try {
                com.omnidroid.lib.saves.Ps3SaveArchive.capture(
                    java.io.File(Armsx3Bridge.baseDir(activity), "config/dev_hdd0/home/00000001/savedata"), id,
                )
            } finally {
                if (!stop && !paused && !stopped.get()) Armsx3Bridge.resume()
            }
        }.get()
        if (data != null) savesManager.setSaveRAM(game, data)
    }

    init {
        displayManager?.registerDisplayListener(displayListener, android.os.Handler(android.os.Looper.getMainLooper()))
        displayListenerRegistered = displayManager != null
        updateDisplayRate()
        container.addView(surfaceView, FrameLayout.LayoutParams(1, 1))
        container.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> layoutSurface() }
    }

    override val view: View get() = container
    override fun asView(): View = container
    override var audioEnabled: Boolean
        get() = !muted
        set(value) { setAudioMuted(!value) }
    override var frameSpeed: Int = 1
    override var shader: ShaderConfig? = null
    override var viewport: RectF? = null
        set(value) { field = value; layoutSurface() }

    private fun layoutSurface() {
        val vp = viewport ?: return
        if (container.width == 0 || container.height == 0) return
        val width = (vp.width() * container.width).toInt().coerceAtLeast(1)
        val height = (vp.height() * container.height).toInt().coerceAtLeast(1)
        val left = (vp.left * container.width).toInt()
        val top = (vp.top * container.height).toInt()
        val params = surfaceView.layoutParams as FrameLayout.LayoutParams
        if (params.width == width && params.height == height && params.leftMargin == left && params.topMargin == top) return
        params.width = width; params.height = height; params.leftMargin = left; params.topMargin = top
        surfaceView.layoutParams = params
    }

    override fun start() { maybeBoot() }
    private fun maybeBoot() {
        if (!surfaceReady || bootQueued || restarting || stopped.get() || viewport == null || surfaceView.width <= 1) return
        bootQueued = true
        Armsx3Bridge.surface(surfaceView.holder.surface, surfaceView.width, surfaceView.height)
        Armsx3Bridge.executor.execute {
            if (stopped.get()) return@execute
            try {
                com.omnidroid.lib.backend.ps3.Ps3GraphicsOptions.all.forEach { option ->
                    applyGraphicsOption(option, preference(option.key, option.default))
                }
                setAudioMuted(muted)
                val result = Armsx3Bridge.boot(game.fileUri)
                if (result != 0) error(Armsx3Bridge.bootError(result))
                activity.runOnUiThread {
                    if (!stopped.get()) {
                        events.tryEmit(GLRetroView.GLRetroEvents.FrameRendered)
                        if (paused) pause()
                        surfaceView.postDelayed(pollState, 500)
                    }
                }
            } catch (failure: Exception) {
                Timber.e(failure, "PS3 boot failed")
                activity.runOnUiThread {
                    if (!stopped.get()) {
                        android.widget.Toast.makeText(activity, failure.message, android.widget.Toast.LENGTH_LONG).show()
                        onExit(-1)
                    }
                }
            }
        }
    }

    private val pollState = object : Runnable {
        override fun run() {
            if (stopped.get() || restarting) return
            when (Armsx3Bridge.state()) {
                0 -> if (Armsx3Bridge.stateOperationActive) surfaceView.postDelayed(this, 100) else { stop(); onExit(0) }
                else -> surfaceView.postDelayed(this, 500)
            }
        }
    }
    override fun pause() {
        paused = true
        releasePads()
        Armsx3Bridge.executor.execute { if (!stopped.get()) Armsx3Bridge.pause() }
    }
    override fun resume() {
        paused = false
        Armsx3Bridge.executor.execute { if (!stopped.get() && surfaceReady) Armsx3Bridge.resume() }
    }
    override fun stop() {
        activity.runOnUiThread { guestDialogs.detach(); releaseDisplayListener() }
        if (!stopped.compareAndSet(false, true)) return
        surfaceView.removeCallbacks(pollState)
        releasePads()
        Armsx3Bridge.executor.execute { Armsx3Bridge.stop() }
    }
    private fun releasePads() = synchronized(pads) { activePorts.forEach { port -> pads[port].release(); pads[port].send(port) } }

    override fun surfaceCreated(holder: SurfaceHolder) { surfaceReady = true }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (stopped.get() || width <= 1 || height <= 1) return
        updateDisplayRate()
        Armsx3Bridge.surface(holder.surface, width, height)
        if (!bootQueued) maybeBoot() else if (!paused) resume()
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        releasePads()
        Armsx3Bridge.executor.execute { if (!surfaceReady) Armsx3Bridge.pause() }
        Armsx3Bridge.surface(null)
    }

    override fun sendNormalizedKey(action: Int, key: NormalizedKey, port: Int): Boolean = synchronized(pads) {
        val pad = pads.getOrNull(port) ?: return false
        val handled = pad.button(key, action == KeyEvent.ACTION_DOWN)
        if (handled) { activePorts += port; pad.send(port) }
        handled
    }
    override fun sendNormalizedMotion(source: NormalizedMotionSource, x: Float, y: Float, port: Int): Boolean = synchronized(pads) {
        val pad = pads.getOrNull(port) ?: return false
        activePorts += port; pad.stick(source, x, y); pad.send(port)
        true
    }
    override fun sendKeyEvent(action: Int, keyCode: Int, port: Int) {
        val key = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> NormalizedKey.BUTTON_A
            KeyEvent.KEYCODE_BUTTON_B -> NormalizedKey.BUTTON_B
            KeyEvent.KEYCODE_BUTTON_X -> NormalizedKey.BUTTON_X
            KeyEvent.KEYCODE_BUTTON_Y -> NormalizedKey.BUTTON_Y
            KeyEvent.KEYCODE_BUTTON_L1 -> NormalizedKey.TRIGGER_L1
            KeyEvent.KEYCODE_BUTTON_R1 -> NormalizedKey.TRIGGER_R1
            KeyEvent.KEYCODE_BUTTON_L2 -> NormalizedKey.TRIGGER_L2
            KeyEvent.KEYCODE_BUTTON_R2 -> NormalizedKey.TRIGGER_R2
            KeyEvent.KEYCODE_BUTTON_THUMBL -> NormalizedKey.BUTTON_THUMB_L
            KeyEvent.KEYCODE_BUTTON_THUMBR -> NormalizedKey.BUTTON_THUMB_R
            KeyEvent.KEYCODE_BUTTON_START -> NormalizedKey.BUTTON_START
            KeyEvent.KEYCODE_BUTTON_SELECT -> NormalizedKey.BUTTON_SELECT
            KeyEvent.KEYCODE_DPAD_UP -> NormalizedKey.DPAD_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> NormalizedKey.DPAD_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> NormalizedKey.DPAD_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> NormalizedKey.DPAD_RIGHT
            KeyEvent.KEYCODE_BUTTON_MODE -> NormalizedKey.BUTTON_HOME
            else -> return
        }
        sendNormalizedKey(action, key, port)
    }
    override fun sendMotionEvent(source: Int, x: Float, y: Float, port: Int) {
        when (source) {
            GLRetroView.MOTION_SOURCE_ANALOG_LEFT -> sendNormalizedMotion(NormalizedMotionSource.LeftStick, x, y, port)
            GLRetroView.MOTION_SOURCE_ANALOG_RIGHT -> sendNormalizedMotion(NormalizedMotionSource.RightStick, x, y, port)
            GLRetroView.MOTION_SOURCE_DPAD -> {
                sendNormalizedKey(if (x < -0.5f) 0 else 1, NormalizedKey.DPAD_LEFT, port)
                sendNormalizedKey(if (x > 0.5f) 0 else 1, NormalizedKey.DPAD_RIGHT, port)
                sendNormalizedKey(if (y < -0.5f) 0 else 1, NormalizedKey.DPAD_UP, port)
                sendNormalizedKey(if (y > 0.5f) 0 else 1, NormalizedKey.DPAD_DOWN, port)
            }
        }
    }
    private fun preference(key: String, default: String): String {
        val raw = preferences.all[CoreVariablesManager.computeSharedPreferenceKey(key, game.systemId)]
        return com.omnidroid.lib.backend.ps3.Ps3GraphicsOptions.all.firstOrNull { it.key == key }?.storedValue(raw) ?: default
    }
    override fun applySetting(key: String, value: String) {
        val option = com.omnidroid.lib.backend.ps3.Ps3GraphicsOptions.all.firstOrNull { it.key == key } ?: return
        // Restart-only options are persisted by Omnidroid and applied before the next boot.
        if (bootQueued && option.restartRequired && Armsx3Bridge.state() != 0) return
        applyGraphicsOption(option, value)
    }
    private fun applyGraphicsOption(option: com.omnidroid.lib.backend.ps3.Ps3GraphicsOptions.Option, value: String) {
        val selected = value.takeIf(option::accepts) ?: option.default
        val json = when (option.kind) {
            com.omnidroid.lib.backend.ps3.Ps3GraphicsOptions.Kind.NUMBER -> selected
            com.omnidroid.lib.backend.ps3.Ps3GraphicsOptions.Kind.BOOL -> (selected == "enabled").toString()
            com.omnidroid.lib.backend.ps3.Ps3GraphicsOptions.Kind.ENUM -> org.json.JSONObject.quote(selected)
        }
        Armsx3Bridge.setting(option.path, json)
    }
    override fun setAudioMuted(muted: Boolean) { this.muted = muted; Armsx3Bridge.setting("Audio@@Master Volume", if (muted) "0" else "100") }
    override fun getVariables() = com.omnidroid.lib.backend.ps3.Ps3GraphicsOptions.all.map { option ->
        Variable(key = option.key, value = preference(option.key, option.default),
            description = "${activity.getString(option.title)}; ${option.choices.joinToString("|") { it.key }}")
    }.toTypedArray()
    override fun updateVariables(vararg variables: Variable) { variables.forEach { variable -> variable.key?.let { key -> variable.value?.let { applySetting(key, it) } } } }
    override fun onTouchEvent(event: MotionEvent) = false
    override fun serializeState(useEmulationThread: Boolean): ByteArray? = null
    override fun unserializeState(state: ByteArray, useEmulationThread: Boolean) = false
    override fun serializeSRAM(useEmulationThread: Boolean): ByteArray? = null
    override fun unserializeSRAM(state: ByteArray, useEmulationThread: Boolean) = false
    override fun reset(useEmulationThread: Boolean) {
        if (stopped.get() || restarting || !bootQueued) return
        restarting = true
        surfaceView.removeCallbacks(pollState)
        releasePads()
        Armsx3Bridge.executor.execute {
            try {
                Armsx3Bridge.stop()
                activity.runOnUiThread {
                    restarting = false
                    bootQueued = false
                    maybeBoot()
                }
            } catch (failure: Exception) {
                Timber.e(failure, "PS3 restart failed")
                activity.runOnUiThread { if (!stopped.get()) onExit(-1) }
            }
        }
    }
    override fun setCheat(index: Int, enabled: Boolean, code: String, useEmulationThread: Boolean) {}
    override fun getAvailableDisks(useEmulationThread: Boolean) = 0
    override fun getCurrentDisk(useEmulationThread: Boolean) = 0
    override fun changeDisk(index: Int, useEmulationThread: Boolean) {}
    override fun setControllerType(port: Int, controllerId: Int) {}
    override fun getControllers(): Array<Array<Controller>>? = null
    override fun getGLRetroEvents(): Flow<GLRetroView.GLRetroEvents> = events
    override fun getGLRetroErrors(): Flow<Int> = emptyFlow()
    override fun getRumbleEvents(): Flow<RumbleEvent> = emptyFlow()
    override fun disableTouchEvents() { surfaceView.setOnTouchListener { _, _ -> true } }
    override fun refreshAspectRatio() { layoutSurface() }
}
