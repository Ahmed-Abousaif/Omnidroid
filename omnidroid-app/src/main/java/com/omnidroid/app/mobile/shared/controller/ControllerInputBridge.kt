package com.omnidroid.app.mobile.shared.controller

import android.app.Activity
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

class ControllerInputBridge {
    var navigation: ControllerNavigationState? = null

    private val activeNavigation get() = navigation?.modalNavigation ?: navigation

    private val stick = StickPressFilter()
    private var lastHatX = 0f
    private var lastHatY = 0f
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

        if (activeNavigation?.textInputActive == true && !isBackOrShoulder(event.keyCode)) {
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
                    activeNavigation?.onBack?.invoke()
                }
                true
            }
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_3,
            KeyEvent.KEYCODE_BUTTON_START,
            -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    activeNavigation?.onOptions?.invoke()
                }
                true
            }
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_4,
            -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    activeNavigation?.onSearch?.invoke()
                }
                true
            }
            KeyEvent.KEYCODE_BUTTON_SELECT,
            -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    activeNavigation?.onScan?.invoke()
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
            // L1 / R1: one console per press, on the way down.
            KeyEvent.KEYCODE_BUTTON_L1 -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    activeNavigation?.onPrevConsole?.invoke()
                }
                true
            }
            KeyEvent.KEYCODE_BUTTON_R1 -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    activeNavigation?.onNextConsole?.invoke()
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
        // The D-pad (hat) is left to Android, which turns it into D-pad keys; that includes the
        // event where it returns to the centre, or the synthesized key would stay down.
        val hatChanged = hatX != lastHatX || hatY != lastHatY
        lastHatX = hatX
        lastHatY = hatY

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

        if (hatActive || hatChanged) {
            stick.reset()
            return false
        }

        // Left stick: one press per tilt (see StickPressFilter), no timers. Every stick event is
        // consumed, so Android doesn't also turn the stick into D-pad keys with its own repeat.
        // Joystick events are batched per frame: feed the older samples too, with their own times.
        for (i in 0 until event.historySize) {
            stick
                .update(
                    event.getHistoricalAxisValue(MotionEvent.AXIS_X, i),
                    event.getHistoricalAxisValue(MotionEvent.AXIS_Y, i),
                    event.getHistoricalEventTime(i),
                )?.let { dispatchPress(it, event.deviceId, event.source, dispatch) }
        }
        stick
            .update(event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y), event.eventTime)
            ?.let { dispatchPress(it, event.deviceId, event.source, dispatch) }
        return true
    }

    private fun dispatchPress(
        press: StickPressFilter.Press,
        deviceId: Int,
        source: Int,
        dispatch: (KeyEvent) -> Boolean,
    ) {
        val now = SystemClock.uptimeMillis()
        dispatchDirectional(press.x, press.y, KeyEvent.ACTION_DOWN, now, deviceId, source, dispatch)
        dispatchDirectional(press.x, press.y, KeyEvent.ACTION_UP, now, deviceId, source, dispatch)
    }

    private fun zoomIn() {
        if (!canZoom()) return
        activeNavigation?.onZoomIn?.invoke()
    }

    private fun zoomOut() {
        if (!canZoom()) return
        activeNavigation?.onZoomOut?.invoke()
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
        time: Long,
        deviceId: Int,
        source: Int,
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
                time,
                time,
                action,
                keyCode,
                0,
                0,
                deviceId,
                0,
                0,
                source,
            ),
        )
    }

    private fun isFromController(event: KeyEvent): Boolean {
        val source = event.source
        return source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
    }

    private fun isBackOrShoulder(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_BUTTON_B ||
            keyCode == KeyEvent.KEYCODE_BUTTON_2 ||
            keyCode == KeyEvent.KEYCODE_BACK ||
            keyCode == KeyEvent.KEYCODE_BUTTON_L1 ||
            keyCode == KeyEvent.KEYCODE_BUTTON_R1

    private fun KeyEvent.remap(keyCode: Int): KeyEvent =
        KeyEvent(
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

    companion object {
        private const val HAT_DEADZONE = 0.5f
        private const val STICK_DEADZONE = 0.6f
        private const val ZOOM_COOLDOWN_MS = 280L
    }

    /**
     * Wraps [dispatch] for keys made from stick movement so they first leave touch mode. After the
     * screen is touched the window is in touch mode, where buttons can't take focus. Real D-pad
     * keys leave it on their way in; keys made from the stick go straight to the window and don't,
     * so focus got stuck on the one item still allowed it (the carousel) until a D-pad press.
     * Build it once per activity and reuse it: motion events stream at up to 250 Hz.
     */
    fun keyed(
        activity: Activity,
        dispatch: (KeyEvent) -> Boolean,
    ): (KeyEvent) -> Boolean =
        { key ->
            val view = activity.currentFocus ?: activity.window.decorView
            if (view.isInTouchMode) view.requestFocusFromTouch()
            dispatch(key)
        }
}
