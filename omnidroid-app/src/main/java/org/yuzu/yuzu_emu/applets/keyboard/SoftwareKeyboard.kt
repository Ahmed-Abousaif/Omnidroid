package org.yuzu.yuzu_emu.applets.keyboard

import androidx.annotation.Keep
import java.io.Serializable

@Keep
object SoftwareKeyboard {
    @Keep
    data class KeyboardConfig(
        var ok_text: String? = null,
        var header_text: String? = null,
        var sub_text: String? = null,
        var guide_text: String? = null,
        var initial_text: String? = null,
        var left_optional_symbol_key: Short = 0,
        var right_optional_symbol_key: Short = 0,
        var max_text_length: Int = 0,
        var min_text_length: Int = 0,
        var initial_cursor_position: Int = 0,
        var type: Int = 0,
        var password_mode: Int = 0,
        var text_draw_type: Int = 0,
        var key_disable_flags: Int = 0,
        var use_blur_background: Boolean = false,
        var enable_backspace_button: Boolean = false,
        var enable_return_button: Boolean = false,
        var disable_cancel_button: Boolean = false,
    ) : Serializable

    @Keep
    data class KeyboardData(var result: Int = 0, var text: String = "")

    @JvmStatic
    fun executeNormal(config: KeyboardConfig): KeyboardData {
        return KeyboardData(0, config.initial_text ?: "")
    }

    @JvmStatic
    fun executeInline(config: KeyboardConfig) {
        // Stubs inline request
    }
}

