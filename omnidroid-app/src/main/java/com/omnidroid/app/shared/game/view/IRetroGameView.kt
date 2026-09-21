package com.omnidroid.app.shared.game.view

import android.graphics.RectF
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.omnidroid.common.graphics.takeScreenshot
import com.swordfish.libretrodroid.Controller
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.RumbleEvent
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.Variable
import kotlinx.coroutines.flow.Flow

/**
 * Common interface for retro game rendering views (OpenGL ES [GLRetroView] and native Vulkan [VulkanRetroView]).
 */
interface IRetroGameView : DefaultLifecycleObserver {

    var audioEnabled: Boolean
    var frameSpeed: Int
    var shader: ShaderConfig?
    var viewport: RectF?

    fun asView(): View

    fun sendKeyEvent(action: Int, keyCode: Int, port: Int = 0)
    fun sendMotionEvent(source: Int, x: Float, y: Float, port: Int = 0)
    fun onTouchEvent(event: MotionEvent): Boolean

    fun serializeState(useEmulationThread: Boolean = true): ByteArray?
    fun unserializeState(state: ByteArray, useEmulationThread: Boolean = true): Boolean
    fun serializeSRAM(useEmulationThread: Boolean = true): ByteArray?
    fun unserializeSRAM(state: ByteArray, useEmulationThread: Boolean = true): Boolean
    fun reset(useEmulationThread: Boolean = true)

    fun setCheat(index: Int, enabled: Boolean, code: String, useEmulationThread: Boolean = true)

    fun getVariables(): Array<Variable>?
    fun updateVariables(vararg variables: Variable)

    fun getAvailableDisks(useEmulationThread: Boolean = true): Int
    fun getCurrentDisk(useEmulationThread: Boolean = true): Int
    fun changeDisk(index: Int, useEmulationThread: Boolean = true)

    fun setControllerType(port: Int, controllerId: Int)
    fun getControllers(): Array<Array<Controller>>?

    fun getGLRetroEvents(): Flow<GLRetroView.GLRetroEvents>
    fun getGLRetroErrors(): Flow<Int>
    fun getRumbleEvents(): Flow<RumbleEvent>

    fun disableTouchEvents()
    fun refreshAspectRatio()
}

suspend fun IRetroGameView.takeScreenshot(maxResolution: Int, retries: Int = 1): android.graphics.Bitmap? {
    val surfaceView = asView() as? SurfaceView ?: return null
    return surfaceView.takeScreenshot(maxResolution, retries)
}

