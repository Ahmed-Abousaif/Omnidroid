package com.omnidroid.app.shared.game.view

import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.LifecycleOwner
import com.omnidroid.common.view.disableTouchEvents
import com.swordfish.libretrodroid.Controller
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.libretrodroid.RumbleEvent
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.Variable
import kotlinx.coroutines.flow.Flow

/**
 * Adapter wrapping [GLRetroView] to satisfy [IRetroGameView].
 */
class GLRetroGameViewWrapper(
    val glRetroView: GLRetroView,
) : IRetroGameView {

    override var audioEnabled: Boolean
        get() = glRetroView.audioEnabled
        set(value) {
            glRetroView.audioEnabled = value
        }

    override var frameSpeed: Int
        get() = glRetroView.frameSpeed
        set(value) {
            glRetroView.frameSpeed = value
        }

    override var shader: ShaderConfig?
        get() = glRetroView.shader
        set(value) {
            if (value != null) {
                glRetroView.shader = value
            }
        }

    override var viewport: RectF?
        get() = glRetroView.viewport
        set(value) {
            if (value != null) {
                glRetroView.viewport = value
            }
        }

    override fun asView(): View = glRetroView

    override fun sendKeyEvent(action: Int, keyCode: Int, port: Int) {
        glRetroView.sendKeyEvent(action, keyCode, port)
    }

    override fun sendMotionEvent(source: Int, x: Float, y: Float, port: Int) {
        glRetroView.sendMotionEvent(source, x, y, port)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return glRetroView.onTouchEvent(event)
    }

    override fun serializeState(useEmulationThread: Boolean): ByteArray? {
        return glRetroView.serializeState(useEmulationThread)
    }

    override fun unserializeState(state: ByteArray, useEmulationThread: Boolean): Boolean {
        return glRetroView.unserializeState(state, useEmulationThread)
    }

    override fun serializeSRAM(useEmulationThread: Boolean): ByteArray? {
        return glRetroView.serializeSRAM(useEmulationThread)
    }

    override fun unserializeSRAM(state: ByteArray, useEmulationThread: Boolean): Boolean {
        return glRetroView.unserializeSRAM(state, useEmulationThread)
    }

    override fun reset(useEmulationThread: Boolean) {
        glRetroView.reset(useEmulationThread)
    }

    override fun setCheat(index: Int, enabled: Boolean, code: String, useEmulationThread: Boolean) {
        glRetroView.setCheat(index, enabled, code, useEmulationThread)
    }

    override fun getVariables(): Array<Variable>? {
        return glRetroView.getVariables()
    }

    override fun updateVariables(vararg variables: Variable) {
        glRetroView.updateVariables(*variables)
    }

    override fun getAvailableDisks(useEmulationThread: Boolean): Int {
        return glRetroView.getAvailableDisks(useEmulationThread)
    }

    override fun getCurrentDisk(useEmulationThread: Boolean): Int {
        return glRetroView.getCurrentDisk(useEmulationThread)
    }

    override fun changeDisk(index: Int, useEmulationThread: Boolean) {
        glRetroView.changeDisk(index, useEmulationThread)
    }

    override fun setControllerType(port: Int, controllerId: Int) {
        glRetroView.setControllerType(port, controllerId)
    }

    override fun getControllers(): Array<Array<Controller>>? {
        return glRetroView.getControllers()
    }

    override fun getGLRetroEvents(): Flow<GLRetroView.GLRetroEvents> {
        return glRetroView.getGLRetroEvents()
    }

    override fun getGLRetroErrors(): Flow<Int> {
        return glRetroView.getGLRetroErrors()
    }

    override fun getRumbleEvents(): Flow<RumbleEvent> {
        return glRetroView.getRumbleEvents()
    }

    override fun disableTouchEvents() {
        glRetroView.disableTouchEvents()
    }

    override fun refreshAspectRatio() {
        runCatching { LibretroDroid.refreshAspectRatio() }
    }

    override fun onCreate(owner: LifecycleOwner) {
        glRetroView.onCreate(owner)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        glRetroView.onDestroy()
    }
}
