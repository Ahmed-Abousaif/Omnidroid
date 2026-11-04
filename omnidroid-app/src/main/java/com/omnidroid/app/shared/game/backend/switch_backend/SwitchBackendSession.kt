package com.omnidroid.app.shared.game.backend.switch_backend

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
import android.view.ViewGroup
import android.widget.FrameLayout
import com.omnidroid.app.shared.game.view.IRetroGameView
import com.omnidroid.app.shared.switch_emu.EdenBridge
import com.omnidroid.lib.backend.BackendCapabilities
import com.omnidroid.lib.backend.BackendSession
import com.omnidroid.lib.backend.NormalizedKey
import com.omnidroid.lib.backend.NormalizedMotionSource
import com.omnidroid.lib.core.CoreVariablesManager
import com.omnidroid.lib.library.SystemCoreConfig
import com.omnidroid.lib.library.db.entity.Game
import com.omnidroid.libretrodroid.Controller
import com.omnidroid.libretrodroid.GLRetroView
import com.omnidroid.libretrodroid.RumbleEvent
import com.omnidroid.libretrodroid.ShaderConfig
import com.omnidroid.libretrodroid.Variable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.features.input.model.NativeAnalog
import org.yuzu.yuzu_emu.features.input.model.NativeButton
import timber.log.Timber

/**
 * Backend session implementation hosting Eden Nintendo Switch emulation inside a SurfaceView.
 * Implements [BackendSession] as well as [IRetroGameView] to seamlessly integrate with
 * Omnidroid's input pipelines (virtual touch controls and physical gamepad maps).
 */
class SwitchBackendSession(
    private val activity: Activity,
    private val game: Game,
    private val systemCoreConfig: SystemCoreConfig,
    private val sharedPreferences: SharedPreferences,
    override val capabilities: BackendCapabilities,
    private val onEmulationExit: (resultCode: Int) -> Unit = {},
) : BackendSession, SurfaceHolder.Callback, NativeLibrary.EmulationCallback, IRetroGameView {

    init {
        runCatching {
            EdenBridge.initialize(activity.applicationContext)
        }.onFailure { e ->
            Timber.w(e, "EdenBridge initialization deferred in SwitchBackendSession.init")
        }
    }

    // Fullscreen host returned to Compose AndroidView. Eden renders into [surfaceView],
    // which is laid out to the normalized [viewport] rect (aspect fit + screen position).
    private val container: FrameLayout =
        FrameLayout(activity).apply {
            isFocusable = false
            isClickable = false
            setBackgroundColor(Color.BLACK)
        }

    private val surfaceView: SurfaceView =
        SurfaceView(activity).apply {
            isFocusable = false
            isClickable = false
            holder.setKeepScreenOn(true)
            holder.setFormat(PixelFormat.RGBX_8888)
            holder.addCallback(this@SwitchBackendSession)
        }

    private val glRetroEvents = MutableSharedFlow<GLRetroView.GLRetroEvents>(replay = 1)
    private var isSurfaceCreated = false
    private var isEmulating = false
    private var isDocked = false
    private var isAudioMuted = false
    private var dpadLeftPressed = false
    private var dpadRightPressed = false
    private var dpadUpPressed = false
    private var dpadDownPressed = false

    init {
        // Start at 1×1 so Eden cannot create a fullscreen swapchain before Compose
        // measures the letterboxed viewport (that race squished landscape on first boot).
        container.addView(
            surfaceView,
            FrameLayout.LayoutParams(1, 1),
        )
        // Parent size is often 0 when viewport is first assigned; re-apply after layout.
        // Skip while viewport is still null so we don't expand to fullscreen before Compose
        // measures the letterbox (that would recreate the landscape squish race).
        container.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (viewport != null) {
                applyViewportLayout(viewport)
            }
        }
    }

    override var audioEnabled: Boolean
        get() = !isAudioMuted
        set(value) {
            setAudioMuted(!value)
        }

    override var frameSpeed: Int = 1
    override var shader: ShaderConfig? = null
    override var viewport: RectF? = null
        set(value) {
            field = value
            applyViewportLayout(value)
        }

    override fun asView(): View = container
    override val view: View
        get() = container

    override fun start() {
        startEmulationIfReady()
    }

    override fun pause() {
        if (isEmulating) {
            EdenBridge.pause()
        }
    }

    override fun resume() {
        if (isEmulating) {
            EdenBridge.resume()
            applyLiveSettings()
        }
    }

    override fun stop() {
        if (isEmulating) {
            EdenBridge.stop()
            isEmulating = false
        }
    }

    // --- IRetroGameView & Input ---

    override fun sendKeyEvent(action: Int, keyCode: Int, port: Int) {
        val button = mapKeyCodeToSwitchButton(keyCode) ?: return
        val isPressed = (action == KeyEvent.ACTION_DOWN)
        EdenBridge.sendOverlayButton(button, isPressed)
    }

    override fun sendMotionEvent(source: Int, x: Float, y: Float, port: Int) {
        when (source) {
            GLRetroView.MOTION_SOURCE_ANALOG_LEFT -> {
                EdenBridge.sendOverlayJoystick(NativeAnalog.LStick, x, -y)
            }
            GLRetroView.MOTION_SOURCE_ANALOG_RIGHT -> {
                EdenBridge.sendOverlayJoystick(NativeAnalog.RStick, x, -y)
            }
            GLRetroView.MOTION_SOURCE_DPAD -> {
                handleDpadMotion(x, y)
            }
        }
    }

    private fun handleDpadMotion(x: Float, y: Float) {
        val left = x < -0.5f
        val right = x > 0.5f
        val up = y < -0.5f
        val down = y > 0.5f

        if (left != dpadLeftPressed) {
            dpadLeftPressed = left
            EdenBridge.sendOverlayButton(NativeButton.DLeft, left)
        }
        if (right != dpadRightPressed) {
            dpadRightPressed = right
            EdenBridge.sendOverlayButton(NativeButton.DRight, right)
        }
        if (up != dpadUpPressed) {
            dpadUpPressed = up
            EdenBridge.sendOverlayButton(NativeButton.DUp, up)
        }
        if (down != dpadDownPressed) {
            dpadDownPressed = down
            EdenBridge.sendOverlayButton(NativeButton.DDown, down)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEmulating) return false
        val actionMasked = event.actionMasked
        val pointerIndex = event.actionIndex
        val fingerId = event.getPointerId(pointerIndex)
        // TouchScreenPointerOverlay maps into [container] space (asView); Eden expects
        // coordinates relative to the SurfaceView swapchain.
        val x = event.getX(pointerIndex) - surfaceView.left
        val y = event.getY(pointerIndex) - surfaceView.top
        EdenBridge.sendTouch(fingerId, x, y, actionMasked)
        return true
    }

    override fun serializeState(useEmulationThread: Boolean): ByteArray? = null
    override fun unserializeState(state: ByteArray, useEmulationThread: Boolean): Boolean = false
    override fun serializeSRAM(useEmulationThread: Boolean): ByteArray? = null
    override fun unserializeSRAM(state: ByteArray, useEmulationThread: Boolean): Boolean = false

    override fun reset(useEmulationThread: Boolean) {
        if (isEmulating) {
            EdenBridge.stop()
            isEmulating = false
            surfaceView.postDelayed({
                startEmulationIfReady()
            }, 200)
        }
    }

    override fun setCheat(index: Int, enabled: Boolean, code: String, useEmulationThread: Boolean) {}

    override fun getVariables(): Array<Variable>? {
        val systemId = game.systemId
        val dockedPrefKey = CoreVariablesManager.computeSharedPreferenceKey("switch_docked_mode", systemId)
        val dockedVal = sharedPreferences.getString(dockedPrefKey, "false") ?: "false"

        val resPrefKey = CoreVariablesManager.computeSharedPreferenceKey("switch_resolution", systemId)
        val resVal = sharedPreferences.getString(resPrefKey, "3") ?: "3"

        val asyncPrefKey = CoreVariablesManager.computeSharedPreferenceKey("switch_async_shaders", systemId)
        val asyncVal = try {
            sharedPreferences.getString(asyncPrefKey, "true") ?: "true"
        } catch (e: ClassCastException) {
            if (sharedPreferences.getBoolean(asyncPrefKey, true)) "true" else "false"
        }

        return arrayOf(
            Variable("switch_docked_mode", "Console Mode; false|true", dockedVal),
            Variable("switch_resolution", "Resolution Scale; 1|2|3|5|6", resVal),
            Variable("switch_async_shaders", "Asynchronous Shaders; true|false", asyncVal),
        )
    }

    override fun updateVariables(vararg variables: Variable) {
        for (variable in variables) {
            val key = variable.key ?: continue
            val value = variable.value ?: continue
            applySetting(key, value)
        }
        applyLiveSettings()
    }

    override fun getAvailableDisks(useEmulationThread: Boolean): Int = 0
    override fun getCurrentDisk(useEmulationThread: Boolean): Int = 0
    override fun changeDisk(index: Int, useEmulationThread: Boolean) {}

    override fun setControllerType(port: Int, controllerId: Int) {}
    override fun getControllers(): Array<Array<Controller>>? = null

    override fun getGLRetroEvents(): Flow<GLRetroView.GLRetroEvents> = glRetroEvents
    override fun getGLRetroErrors(): Flow<Int> = emptyFlow()
    override fun getRumbleEvents(): Flow<RumbleEvent> = emptyFlow()

    override fun disableTouchEvents() {
        // Match libretro: keep raw View touches from competing with Compose pads /
        // TouchScreenPointerOverlay. Stylus is forwarded via IRetroGameView.onTouchEvent.
        val sink = View.OnTouchListener { _, _ -> true }
        container.setOnTouchListener(sink)
        surfaceView.setOnTouchListener(sink)
    }

    override fun refreshAspectRatio() {}

    /**
     * Lays out [surfaceView] inside [container] to the normalized viewport rect that
     * MobileGameScreen derives from aspect fit + screen-position bias. Eden has no
     * GPU-viewport API, so the Surface size itself is the letterbox.
     */
    private fun applyViewportLayout(viewport: RectF?) {
        val apply =
            Runnable {
                val parentW = container.width
                val parentH = container.height
                if (parentW <= 0 || parentH <= 0) return@Runnable

                val lp =
                    (surfaceView.layoutParams as? FrameLayout.LayoutParams)
                        ?: FrameLayout.LayoutParams(0, 0)

                val frame = viewportPixelFrame(viewport, parentW, parentH)

                if (lp.width == frame.width &&
                    lp.height == frame.height &&
                    lp.leftMargin == frame.left &&
                    lp.topMargin == frame.top
                ) {
                    // Layout already correct; surfaceChanged may not re-fire.
                    startEmulationIfReady()
                    return@Runnable
                }

                lp.width = frame.width
                lp.height = frame.height
                lp.leftMargin = frame.left
                lp.topMargin = frame.top
                lp.rightMargin = 0
                lp.bottomMargin = 0
                surfaceView.layoutParams = lp
            }
        if (container.isInLayout) {
            container.post(apply)
        } else {
            apply.run()
        }
    }

    private fun viewportPixelFrame(
        viewport: RectF?,
        parentW: Int,
        parentH: Int,
    ): ViewportFrame {
        if (viewport == null) {
            return ViewportFrame(parentW, parentH, 0, 0)
        }
        // Derive size from width()/height() so independent edge rounding cannot change aspect.
        val left = (viewport.left * parentW).toInt()
        val top = (viewport.top * parentH).toInt()
        val width = (viewport.width() * parentW).toInt().coerceAtLeast(1)
        val height = (viewport.height() * parentH).toInt().coerceAtLeast(1)
        return ViewportFrame(width, height, left, top)
    }

    private fun isSurfaceLaidOutToViewport(): Boolean {
        val vp = viewport ?: return false
        val parentW = container.width
        val parentH = container.height
        if (parentW <= 0 || parentH <= 0) return false
        val expected = viewportPixelFrame(vp, parentW, parentH)
        return surfaceView.width == expected.width &&
            surfaceView.height == expected.height &&
            surfaceView.left == expected.left &&
            surfaceView.top == expected.top
    }

    private data class ViewportFrame(val width: Int, val height: Int, val left: Int, val top: Int)

    // --- BackendSession Normalized Input ---

    override fun sendNormalizedKey(action: Int, key: NormalizedKey, port: Int): Boolean {
        val button = mapNormalizedKeyToSwitchButton(key) ?: return false
        val isPressed = (action == KeyEvent.ACTION_DOWN)
        EdenBridge.sendOverlayButton(button, isPressed)
        return true
    }

    override fun sendNormalizedMotion(source: NormalizedMotionSource, x: Float, y: Float, port: Int): Boolean {
        val analog = when (source) {
            is NormalizedMotionSource.LeftStick -> NativeAnalog.LStick
            is NormalizedMotionSource.RightStick -> NativeAnalog.RStick
        }
        EdenBridge.sendOverlayJoystick(analog, x, -y)
        return true
    }

    override fun applySetting(key: String, value: String) {
        when (key) {
            "switch_docked_mode" -> {
                val docked = (value == "true")
                isDocked = docked
                EdenBridge.setDockedMode(docked)
                EdenBridge.configureControllers(docked)
            }
            "switch_resolution" -> EdenBridge.setResolutionSetup(value.toIntOrNull() ?: 3)
            "switch_async_shaders" -> {
                val enabled = (value == "true" || value == "enabled")
                EdenBridge.setAsyncShaders(enabled)
            }
        }
    }

    override fun setAudioMuted(muted: Boolean) {
        isAudioMuted = muted
        EdenBridge.setAudioMuted(muted)
    }

    fun applyLiveSettings() {
        val systemId = game.systemId
        val dockedPrefKey = CoreVariablesManager.computeSharedPreferenceKey("switch_docked_mode", systemId)
        val docked = sharedPreferences.getString(dockedPrefKey, "false") == "true"
        isDocked = docked
        EdenBridge.setDockedMode(docked)
        EdenBridge.configureControllers(docked)

        val resPrefKey = CoreVariablesManager.computeSharedPreferenceKey("switch_resolution", systemId)
        val resSetup = sharedPreferences.getString(resPrefKey, "3")?.toIntOrNull() ?: 3
        EdenBridge.setResolutionSetup(resSetup)

        val asyncPrefKey = CoreVariablesManager.computeSharedPreferenceKey("switch_async_shaders", systemId)
        val asyncEnabled = try {
            sharedPreferences.getString(asyncPrefKey, "true") != "false"
        } catch (e: ClassCastException) {
            sharedPreferences.getBoolean(asyncPrefKey, true)
        }
        EdenBridge.setAsyncShaders(asyncEnabled)
    }

    // --- SurfaceHolder.Callback ---

    override fun surfaceCreated(holder: SurfaceHolder) {
        isSurfaceCreated = true
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (!isEmulating) {
            startEmulationIfReady()
        } else if (width > 1 && height > 1) {
            EdenBridge.onSurfaceChanged(holder.surface)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isSurfaceCreated = false
        EdenBridge.onSurfaceDestroyed()
    }

    // --- NativeLibrary.EmulationCallback ---

    override fun onEmulationStarted() {
        glRetroEvents.tryEmit(GLRetroView.GLRetroEvents.FrameRendered)
        surfaceView.post {
            EdenBridge.configureControllers(isDocked)
        }
    }
    override fun onEmulationStopped(status: Int) {}
    override fun onProgramChanged(programIndex: Int) {}

    override fun onExitEmulation(resultCode: Int) {
        activity.runOnUiThread {
            onEmulationExit(resultCode)
        }
    }

    private fun startEmulationIfReady() {
        if (!isSurfaceCreated || isEmulating) return
        // Wait until the SurfaceView has been laid out to the Compose viewport. Starting
        // against the pre-letterbox size makes Eden lock a stretched swapchain until the
        // next surface recreation (e.g. orientation change).
        if (!isSurfaceLaidOutToViewport()) return

        if (!EdenBridge.areKeysPresent(activity)) {
            Timber.w("Switch keys missing when attempting to start emulation")
            return
        }

        if (!NativeLibrary.isLoaded) {
            Timber.w("Switch native library not loaded when starting emulation; attempting initialization")
            runCatching { EdenBridge.initialize(activity.applicationContext) }
            if (!NativeLibrary.isLoaded) {
                Timber.e("Switch native library could not be loaded; aborting emulation")
                activity.runOnUiThread {
                    onEmulationExit(-1)
                }
                return
            }
        }

        isEmulating = true
        EdenBridge.ensureGpuDriver(activity)

        val systemId = game.systemId
        val dockedPrefKey = CoreVariablesManager.computeSharedPreferenceKey("switch_docked_mode", systemId)
        val docked = sharedPreferences.getString(dockedPrefKey, "false") == "true"
        isDocked = docked

        val resPrefKey = CoreVariablesManager.computeSharedPreferenceKey("switch_resolution", systemId)
        val resSetup = sharedPreferences.getString(resPrefKey, "3")?.toIntOrNull() ?: 3

        val asyncPrefKey = CoreVariablesManager.computeSharedPreferenceKey("switch_async_shaders", systemId)
        val asyncEnabled = try {
            sharedPreferences.getString(asyncPrefKey, "true") != "false"
        } catch (e: ClassCastException) {
            sharedPreferences.getBoolean(asyncPrefKey, true)
        }

        val targetPath = if (game.fileUri.startsWith("file://", ignoreCase = true)) {
            android.net.Uri.parse(game.fileUri).path ?: game.fileUri
        } else {
            game.fileUri
        }

        EdenBridge.startEmulation(
            targetPath,
            surfaceView.holder.surface,
            docked,
            resSetup,
            isAudioMuted,
            asyncEnabled,
            this,
        )
        glRetroEvents.tryEmit(GLRetroView.GLRetroEvents.FrameRendered)
    }

    companion object {
        fun mapKeyCodeToSwitchButton(keyCode: Int): NativeButton? {
            return when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> NativeButton.A
                KeyEvent.KEYCODE_BUTTON_B -> NativeButton.B
                KeyEvent.KEYCODE_BUTTON_X -> NativeButton.X
                KeyEvent.KEYCODE_BUTTON_Y -> NativeButton.Y
                KeyEvent.KEYCODE_BUTTON_L1 -> NativeButton.L
                KeyEvent.KEYCODE_BUTTON_R1 -> NativeButton.R
                KeyEvent.KEYCODE_BUTTON_L2 -> NativeButton.ZL
                KeyEvent.KEYCODE_BUTTON_R2 -> NativeButton.ZR
                KeyEvent.KEYCODE_BUTTON_START -> NativeButton.Plus
                KeyEvent.KEYCODE_BUTTON_SELECT -> NativeButton.Minus
                KeyEvent.KEYCODE_BUTTON_THUMBL -> NativeButton.LStick
                KeyEvent.KEYCODE_BUTTON_THUMBR -> NativeButton.RStick
                KeyEvent.KEYCODE_DPAD_UP -> NativeButton.DUp
                KeyEvent.KEYCODE_DPAD_DOWN -> NativeButton.DDown
                KeyEvent.KEYCODE_DPAD_LEFT -> NativeButton.DLeft
                KeyEvent.KEYCODE_DPAD_RIGHT -> NativeButton.DRight
                KeyEvent.KEYCODE_BUTTON_MODE -> NativeButton.Home
                else -> null
            }
        }

        fun mapNormalizedKeyToSwitchButton(key: NormalizedKey): NativeButton? {
            return when (key) {
                NormalizedKey.BUTTON_A -> NativeButton.A
                NormalizedKey.BUTTON_B -> NativeButton.B
                NormalizedKey.BUTTON_X -> NativeButton.X
                NormalizedKey.BUTTON_Y -> NativeButton.Y
                NormalizedKey.TRIGGER_L1 -> NativeButton.L
                NormalizedKey.TRIGGER_R1 -> NativeButton.R
                NormalizedKey.TRIGGER_L2 -> NativeButton.ZL
                NormalizedKey.TRIGGER_R2 -> NativeButton.ZR
                NormalizedKey.BUTTON_START -> NativeButton.Plus
                NormalizedKey.BUTTON_SELECT -> NativeButton.Minus
                NormalizedKey.BUTTON_THUMB_L -> NativeButton.LStick
                NormalizedKey.BUTTON_THUMB_R -> NativeButton.RStick
                NormalizedKey.DPAD_UP -> NativeButton.DUp
                NormalizedKey.DPAD_DOWN -> NativeButton.DDown
                NormalizedKey.DPAD_LEFT -> NativeButton.DLeft
                NormalizedKey.DPAD_RIGHT -> NativeButton.DRight
                NormalizedKey.BUTTON_HOME -> NativeButton.Home
                NormalizedKey.BUTTON_CAPTURE -> NativeButton.Capture
            }
        }
    }
}
