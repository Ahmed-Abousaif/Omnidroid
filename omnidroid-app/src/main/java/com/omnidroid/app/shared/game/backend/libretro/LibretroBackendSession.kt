package com.omnidroid.app.shared.game.backend.libretro

import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.View
import com.omnidroid.app.shared.game.view.IRetroGameView
import com.omnidroid.app.shared.game.view.takeScreenshot
import com.omnidroid.lib.backend.BackendCapabilities
import com.omnidroid.lib.backend.BackendSession
import com.omnidroid.lib.backend.DiscSwappableSession
import com.omnidroid.lib.backend.FastForwardableSession
import com.omnidroid.lib.backend.NormalizedKey
import com.omnidroid.lib.backend.NormalizedMotionSource
import com.omnidroid.lib.backend.RewindableSession
import com.omnidroid.lib.backend.ScreenshotSession
import com.omnidroid.lib.backend.StateSaveableSession
import com.swordfish.libretrodroid.Variable

/**
 * Adapter wrapping [IRetroGameView] to satisfy [BackendSession] and optional capability interfaces.
 */
class LibretroBackendSession(
    private val retroGameView: IRetroGameView,
    override val capabilities: BackendCapabilities,
) : BackendSession,
    StateSaveableSession,
    DiscSwappableSession,
    FastForwardableSession,
    RewindableSession,
    ScreenshotSession {

    override val view: View
        get() = retroGameView.asView()

    override fun start() {
        // Lifecycle is managed via DefaultLifecycleObserver on retroGameView
    }

    override fun pause() {
        // Handled via Lifecycle
    }

    override fun resume() {
        // Handled via Lifecycle
    }

    override fun stop() {
        // Handled via Lifecycle
    }

    override fun sendNormalizedKey(action: Int, key: NormalizedKey, port: Int): Boolean {
        val keyCode = mapNormalizedKeyToRetropad(key) ?: return false
        retroGameView.sendKeyEvent(action, keyCode, port)
        return true
    }

    override fun sendNormalizedMotion(source: NormalizedMotionSource, x: Float, y: Float, port: Int): Boolean {
        val src = when (source) {
            is NormalizedMotionSource.LeftStick -> 0
            is NormalizedMotionSource.RightStick -> 1
        }
        retroGameView.sendMotionEvent(src, x, y, port)
        return true
    }

    override fun applySetting(key: String, value: String) {
        retroGameView.updateVariables(Variable(key, value))
    }

    override fun setAudioMuted(muted: Boolean) {
        retroGameView.audioEnabled = !muted
    }

    // --- StateSaveableSession ---

    override suspend fun saveState(slot: Int): Boolean {
        val data = retroGameView.serializeState(useEmulationThread = true) ?: return false
        return data.isNotEmpty()
    }

    override suspend fun loadState(slot: Int): Boolean {
        return true
    }

    override suspend fun getAutoSaveState(): ByteArray? {
        return retroGameView.serializeState(useEmulationThread = true)
    }

    override suspend fun restoreAutoSaveState(state: ByteArray): Boolean {
        return retroGameView.unserializeState(state, useEmulationThread = true)
    }

    // --- DiscSwappableSession ---

    override val totalDiscs: Int
        get() = retroGameView.getAvailableDisks(useEmulationThread = true)

    override val currentDisc: Int
        get() = retroGameView.getCurrentDisk(useEmulationThread = true)

    override suspend fun changeDisc(discIndex: Int): Boolean {
        retroGameView.changeDisk(discIndex, useEmulationThread = true)
        return true
    }

    // --- FastForwardableSession ---

    override fun setFastForwardEnabled(enabled: Boolean) {
        retroGameView.frameSpeed = if (enabled) 2 else 1
    }

    override fun setSpeedMultiplier(multiplier: Float) {
        retroGameView.frameSpeed = multiplier.toInt().coerceAtLeast(1)
    }

    // --- RewindableSession ---

    override fun setRewindEnabled(enabled: Boolean) {
        // Libretro rewind support
    }

    // --- ScreenshotSession ---

    override suspend fun captureScreenshot(): Bitmap? {
        return retroGameView.takeScreenshot(maxResolution = 1080)
    }

    companion object {
        fun mapNormalizedKeyToRetropad(key: NormalizedKey): Int? {
            return when (key) {
                NormalizedKey.BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
                NormalizedKey.BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
                NormalizedKey.BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
                NormalizedKey.BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
                NormalizedKey.TRIGGER_L1 -> KeyEvent.KEYCODE_BUTTON_L1
                NormalizedKey.TRIGGER_R1 -> KeyEvent.KEYCODE_BUTTON_R1
                NormalizedKey.TRIGGER_L2 -> KeyEvent.KEYCODE_BUTTON_L2
                NormalizedKey.TRIGGER_R2 -> KeyEvent.KEYCODE_BUTTON_R2
                NormalizedKey.BUTTON_START -> KeyEvent.KEYCODE_BUTTON_START
                NormalizedKey.BUTTON_SELECT -> KeyEvent.KEYCODE_BUTTON_SELECT
                NormalizedKey.BUTTON_THUMB_L -> KeyEvent.KEYCODE_BUTTON_THUMBL
                NormalizedKey.BUTTON_THUMB_R -> KeyEvent.KEYCODE_BUTTON_THUMBR
                NormalizedKey.DPAD_UP -> KeyEvent.KEYCODE_DPAD_UP
                NormalizedKey.DPAD_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
                NormalizedKey.DPAD_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
                NormalizedKey.DPAD_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
                NormalizedKey.BUTTON_HOME -> null
                NormalizedKey.BUTTON_CAPTURE -> null
            }
        }
    }
}

