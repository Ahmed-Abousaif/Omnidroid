package com.omnidroid.lib.backend

/**
 * Standardized logical gamepad inputs emitted by Omnidroid's input pipeline
 * (both physical gamepad mapping and virtual touch controls).
 */
enum class NormalizedKey {
    BUTTON_A,
    BUTTON_B,
    BUTTON_X,
    BUTTON_Y,
    TRIGGER_L1,
    TRIGGER_R1,
    TRIGGER_L2,
    TRIGGER_R2,
    BUTTON_START,
    BUTTON_SELECT,
    BUTTON_THUMB_L,
    BUTTON_THUMB_R,
    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT,
    BUTTON_HOME,
    BUTTON_CAPTURE,
}

sealed class NormalizedMotionSource {
    object LeftStick : NormalizedMotionSource()
    object RightStick : NormalizedMotionSource()
}

