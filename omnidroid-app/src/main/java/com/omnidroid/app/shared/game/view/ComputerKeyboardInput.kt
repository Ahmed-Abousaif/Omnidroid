package com.omnidroid.app.shared.game.view

import android.view.InputDevice
import android.view.KeyEvent
import com.omnidroid.lib.library.SystemID

/** SDL/libretro key symbols; independent of Android joypad key codes. */
object ComputerKeyboardInput {
    val systems = setOf(SystemID.ATARI800, SystemID.C64, SystemID.MSX, SystemID.MSX2, SystemID.APPLE2)

    fun symbol(code: Int): Int? =
        when (code) {
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> 'a'.code + code - KeyEvent.KEYCODE_A
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> '0'.code + code - KeyEvent.KEYCODE_0
            in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> 282 + code - KeyEvent.KEYCODE_F1
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> 13
            KeyEvent.KEYCODE_DEL -> 8
            KeyEvent.KEYCODE_FORWARD_DEL -> 127
            KeyEvent.KEYCODE_TAB -> 9
            KeyEvent.KEYCODE_ESCAPE -> 27
            KeyEvent.KEYCODE_SPACE -> 32
            KeyEvent.KEYCODE_DPAD_UP -> 273
            KeyEvent.KEYCODE_DPAD_DOWN -> 274
            KeyEvent.KEYCODE_DPAD_RIGHT -> 275
            KeyEvent.KEYCODE_DPAD_LEFT -> 276
            KeyEvent.KEYCODE_INSERT -> 277
            KeyEvent.KEYCODE_MOVE_HOME -> 278
            KeyEvent.KEYCODE_MOVE_END -> 279
            KeyEvent.KEYCODE_PAGE_UP -> 280
            KeyEvent.KEYCODE_PAGE_DOWN -> 281
            KeyEvent.KEYCODE_SHIFT_LEFT -> 304
            KeyEvent.KEYCODE_SHIFT_RIGHT -> 303
            KeyEvent.KEYCODE_CTRL_LEFT -> 306
            KeyEvent.KEYCODE_CTRL_RIGHT -> 305
            KeyEvent.KEYCODE_ALT_LEFT -> 308
            KeyEvent.KEYCODE_ALT_RIGHT -> 307
            KeyEvent.KEYCODE_CAPS_LOCK -> 301
            KeyEvent.KEYCODE_COMMA -> ','.code
            KeyEvent.KEYCODE_PERIOD -> '.'.code
            KeyEvent.KEYCODE_MINUS -> '-'.code
            KeyEvent.KEYCODE_EQUALS -> '='.code
            KeyEvent.KEYCODE_LEFT_BRACKET -> '['.code
            KeyEvent.KEYCODE_RIGHT_BRACKET -> ']'.code
            KeyEvent.KEYCODE_BACKSLASH -> 92
            KeyEvent.KEYCODE_SEMICOLON -> ';'.code
            KeyEvent.KEYCODE_APOSTROPHE -> 39
            KeyEvent.KEYCODE_SLASH -> '/'.code
            KeyEvent.KEYCODE_GRAVE -> '`'.code
            else -> null
        }

    fun character(
        key: Int,
        shift: Boolean,
        control: Boolean,
    ): Int {
        if (control && key in 97..122) return key - 96
        if (shift && key in 97..122) return key - 32
        if (shift) {
            val plain = "1234567890-=[];'`,./"
            val shifted = listOf(33, 64, 35, 36, 37, 94, 38, 42, 40, 41, 95, 43, 123, 125, 58, 34, 126, 60, 62, 63)
            val index = plain.indexOf(key.toChar())
            if (index >= 0) return shifted[index]
            if (key == 92) return 124
        }
        return if (key in 32..126 || key == 13) key else 0
    }

    fun forward(
        view: IRetroGameView?,
        event: KeyEvent,
    ): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            event.isFromSource(InputDevice.SOURCE_JOYSTICK)
        ) {
            return false
        }
        if (!event.isFromSource(InputDevice.SOURCE_KEYBOARD)) return false
        val key = symbol(event.keyCode) ?: return false
        val modifiers =
            (if (event.isShiftPressed) 1 else 0) or (if (event.isCtrlPressed) 64 else 0) or
                (if (event.isAltPressed) 256 else 0)
        return view?.sendKeyboardEvent(
            event.action == KeyEvent.ACTION_DOWN,
            key,
            if (event.action ==
                KeyEvent.ACTION_DOWN
            ) {
                event.unicodeChar
            } else {
                0
            },
            modifiers,
        ) ==
            true
    }
}
