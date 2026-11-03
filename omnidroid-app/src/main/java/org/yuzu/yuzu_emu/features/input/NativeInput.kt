package org.yuzu.yuzu_emu.features.input

import androidx.annotation.Keep
import org.yuzu.yuzu_emu.features.input.model.NativeAnalog
import org.yuzu.yuzu_emu.features.input.model.NativeButton

@Keep
object NativeInput {
    const val Player1Device = 0
    const val Player2Device = 1
    const val Player3Device = 2
    const val Player4Device = 3
    const val ConsoleDevice = 8

    object ButtonState {
        const val RELEASED = 0
        const val PRESSED = 1
    }

    external fun isHandheldOnly(): Boolean

    external fun onGamePadButtonEvent(
        guid: String,
        port: Int,
        buttonId: Int,
        action: Int,
    )

    external fun onGamePadAxisEvent(
        guid: String,
        port: Int,
        axis: Int,
        value: Float,
    )

    external fun onGamePadMotionEvent(
        guid: String,
        port: Int,
        deltaTimestamp: Long,
        xGyro: Float,
        yGyro: Float,
        zGyro: Float,
        xAccel: Float,
        yAccel: Float,
        zAccel: Float,
    )

    external fun onTouchPressed(fingerId: Int, xAxis: Float, yAxis: Float)

    external fun onTouchMoved(fingerId: Int, xAxis: Float, yAxis: Float)

    external fun onTouchReleased(fingerId: Int)

    fun onOverlayButtonEvent(port: Int, button: NativeButton, action: Int) =
        onOverlayButtonEventImpl(port, button.int, action)

    private external fun onOverlayButtonEventImpl(port: Int, buttonId: Int, action: Int)

    fun onOverlayJoystickEvent(port: Int, stick: NativeAnalog, xAxis: Float, yAxis: Float) =
        onOverlayJoystickEventImpl(port, stick.int, xAxis, yAxis)

    private external fun onOverlayJoystickEventImpl(
        port: Int,
        stickId: Int,
        xAxis: Float,
        yAxis: Float,
    )

    fun getStyleIndex(playerIndex: Int): org.yuzu.yuzu_emu.features.input.model.NpadStyleIndex =
        org.yuzu.yuzu_emu.features.input.model.NpadStyleIndex.from(getStyleIndexImpl(playerIndex))

    private external fun getStyleIndexImpl(playerIndex: Int): Int

    fun setStyleIndex(playerIndex: Int, style: org.yuzu.yuzu_emu.features.input.model.NpadStyleIndex) =
        setStyleIndexImpl(playerIndex, style.int)

    private external fun setStyleIndexImpl(playerIndex: Int, styleIndex: Int)

    external fun getIsConnected(playerIndex: Int): Boolean

    fun connectControllers(playerIndex: Int, connected: Boolean = true) {
        val connectedControllers = BooleanArray(8) { i ->
            if (connected) i <= playerIndex else i < playerIndex
        }
        connectControllersImpl(connectedControllers)
    }

    private external fun connectControllersImpl(connected: BooleanArray)
}

