package com.omnidroid.app.shared.game.view

import android.view.InputDevice
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ComputerKeyboardInputTest {
    @Test fun shiftedKeyboardLettersKeepTheirKeySymbolAndTextCharacter() {
        val received = mutableListOf<List<Any?>>()
        val view =
            Proxy.newProxyInstance(
                IRetroGameView::class.java.classLoader,
                arrayOf(IRetroGameView::class.java),
            ) { _, method, args ->
                if (method.name == "sendKeyboardEvent") {
                    received += args.toList()
                    true
                } else {
                    null
                }
            } as IRetroGameView
        val down =
            KeyEvent(
                0,
                1,
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_A,
                0,
                KeyEvent.META_SHIFT_ON,
                -1,
                0,
                0,
                InputDevice.SOURCE_KEYBOARD,
            )
        assertTrue(ComputerKeyboardInput.forward(view, down))
        assertEquals(listOf(true, 97, 65, 1), received.single())
        assertTrue(ComputerKeyboardInput.forward(view, KeyEvent.changeAction(down, KeyEvent.ACTION_UP)))
        assertEquals(listOf(false, 97, 0, 1), received.last())
    }

    @Test fun onscreenKeyboardProducesShiftedPunctuationAndControlCharacters() {
        assertEquals(33, ComputerKeyboardInput.character(49, shift = true, control = false))
        assertEquals(65, ComputerKeyboardInput.character(97, shift = true, control = false))
        assertEquals(3, ComputerKeyboardInput.character(99, shift = false, control = true))
        assertEquals(0, ComputerKeyboardInput.character(282, shift = false, control = false))
    }

    @Test fun gamepadsAndAndroidBackNeverBecomeTyping() {
        assertNull(ComputerKeyboardInput.symbol(KeyEvent.KEYCODE_BACK))
        val event =
            KeyEvent(0, 1, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 0, 0, -1, 0, 0, InputDevice.SOURCE_GAMEPAD)
        assertFalse(ComputerKeyboardInput.forward(null, event))
        assertEquals(13, ComputerKeyboardInput.symbol(KeyEvent.KEYCODE_ENTER))
        assertEquals(282, ComputerKeyboardInput.symbol(KeyEvent.KEYCODE_F1))
    }
}
