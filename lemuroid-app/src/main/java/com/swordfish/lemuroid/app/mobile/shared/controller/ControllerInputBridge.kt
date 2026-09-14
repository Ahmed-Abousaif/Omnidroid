package com.swordfish.lemuroid.app.mobile.shared.controller

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

class ControllerInputBridge {
    var navigation: ControllerNavigationState? = null

    private var lastX = 0
    private var lastY = 0
    private var lastRepeatAt = 0L
    private var lastL2Pressed = false
    private var lastR2Pressed = false
    private var lastZoomAt = 0L

    fun dispatchKey(
        event: KeyEvent,
        dispatch: (KeyEvent) -> Boolean,
    ): Boolean {
        if (!isFromController(event)) {
            return dispatch(event)
        }

        if (navigation?.textInputActive == true && !isBackOrShoulder(event.keyCode)) {
            return dispatch(event)
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_1,
            -> dispatch(event.remap(KeyEvent.KEYCODE_DPAD_CENTER))
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_2,
            -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    navigation?.onBack?.invoke()
                }
                true
            }
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_3,
            KeyEvent.KEYCODE_BUTTON_START,
            -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    navigation?.onOptions?.invoke()
                }
                true
            }
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_4,
            -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    navigation?.onSearch?.invoke()
                }
                true
            }
            KeyEvent.KEYCODE_BUTTON_SELECT,
            -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    navigation?.onScan?.invoke()
                }
                true
            }
            KeyEvent.KEYCODE_BUTTON_L2 -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    zoomOut()
                }
                true
            }
            KeyEvent.KEYCODE_BUTTON_R2 -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    zoomIn()
                }
                true
            }
            KeyEvent.KEYCODE_BUTTON_L1 -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    navigation?.onPrevConsole?.invoke()
                }
                true
            }
            KeyEvent.KEYCODE_BUTTON_R1 -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    navigation?.onNextConsole?.invoke()
                }
                true
            }
            else -> dispatch(event)
        }
    }

    fun dispatchMotion(
        event: MotionEvent,
        dispatch: (KeyEvent) -> Boolean,
    ): Boolean {
        val device = event.device ?: return false
        if (!device.supportsSource(InputDevice.SOURCE_JOYSTICK) &&
            !device.supportsSource(InputDevice.SOURCE_GAMEPAD)
        ) {
            return false
        }

        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val hatActive = abs(hatX) > HAT_DEADZONE || abs(hatY) > HAT_DEADZONE
        val x = if (hatActive) 0 else axisDirection(0f, event.getAxisValue(MotionEvent.AXIS_X))
        val y = if (hatActive) 0 else axisDirection(0f, event.getAxisValue(MotionEvent.AXIS_Y))

        val l2 =
            maxOf(
                event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_BRAKE),
            )
        val r2 =
            maxOf(
                event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_GAS),
            )
        val l2Pressed = l2 > STICK_DEADZONE
        val r2Pressed = r2 > STICK_DEADZONE
        if (l2Pressed && !lastL2Pressed) {
            zoomOut()
        }
        if (r2Pressed && !lastR2Pressed) {
            zoomIn()
        }
        lastL2Pressed = l2Pressed
        lastR2Pressed = r2Pressed

        val now = SystemClock.uptimeMillis()
        val changed = x != lastX || y != lastY
        val shouldRepeat =
            (x != 0 || y != 0) && now - lastRepeatAt >= STICK_REPEAT_MS

        if (!changed && !shouldRepeat) {
            return l2Pressed || r2Pressed
        }

        if (changed) {
            if (lastX != 0) dispatchDirectional(lastX, 0, KeyEvent.ACTION_UP, event, dispatch)
            if (lastY != 0) dispatchDirectional(0, lastY, KeyEvent.ACTION_UP, event, dispatch)
            lastX = x
            lastY = y
        }

        if (x != 0) dispatchDirectional(x, 0, KeyEvent.ACTION_DOWN, event, dispatch)
        if (y != 0) dispatchDirectional(0, y, KeyEvent.ACTION_DOWN, event, dispatch)
        lastRepeatAt = now
        return true
    }

    private fun zoomIn() {
        if (!canZoom()) return
        navigation?.onZoomIn?.invoke()
    }

    private fun zoomOut() {
        if (!canZoom()) return
        navigation?.onZoomOut?.invoke()
    }

    private fun canZoom(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastZoomAt < ZOOM_COOLDOWN_MS) return false
        lastZoomAt = now
        return true
    }

    private fun dispatchDirectional(
        x: Int,
        y: Int,
        action: Int,
        source: MotionEvent,
        dispatch: (KeyEvent) -> Boolean,
    ) {
        val keyCode =
            when {
                x < 0 -> KeyEvent.KEYCODE_DPAD_LEFT
                x > 0 -> KeyEvent.KEYCODE_DPAD_RIGHT
                y < 0 -> KeyEvent.KEYCODE_DPAD_UP
                y > 0 -> KeyEvent.KEYCODE_DPAD_DOWN
                else -> return
            }
        dispatch(
            KeyEvent(
                source.downTime,
                source.eventTime,
                action,
                keyCode,
                0,
                0,
                source.deviceId,
                0,
                0,
                source.source,
            ),
        )
    }

    private fun axisDirection(
        hat: Float,
        stick: Float,
    ): Int {
        val value = if (abs(hat) > HAT_DEADZONE) hat else stick
        return when {
            value <= -STICK_DEADZONE -> -1
            value >= STICK_DEADZONE -> 1
            else -> 0
        }
    }

    private fun isFromController(event: KeyEvent): Boolean {
        val source = event.source
        return source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
    }

    private fun isBackOrShoulder(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_BUTTON_B ||
            keyCode == KeyEvent.KEYCODE_BUTTON_2 ||
            keyCode == KeyEvent.KEYCODE_BACK ||
            keyCode == KeyEvent.KEYCODE_BUTTON_L1 ||
            keyCode == KeyEvent.KEYCODE_BUTTON_R1
    }

    private fun KeyEvent.remap(keyCode: Int): KeyEvent {
        return KeyEvent(
            downTime,
            eventTime,
            action,
            keyCode,
            repeatCount,
            metaState,
            deviceId,
            scanCode,
            flags,
            source,
        )
    }

    companion object {
        private const val HAT_DEADZONE = 0.5f
        private const val STICK_DEADZONE = 0.6f
        private const val STICK_REPEAT_MS = 220L
        private const val ZOOM_COOLDOWN_MS = 280L
    }
}
