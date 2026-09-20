package com.omnidroid.touchinput.radial.layouts.shared

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.omnidroid.touchinput.R
import com.omnidroid.touchinput.radial.controls.OmnidroidControlAnalog
import com.omnidroid.touchinput.radial.controls.OmnidroidControlButton
import com.omnidroid.touchinput.radial.settings.TouchControllerSettingsManager
import gg.padkit.PadKitScope
import gg.padkit.ids.Id
import gg.padkit.layouts.radial.secondarydials.LayoutRadialSecondaryDialsScope

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonSelect(position: Int = 0) {
    OmnidroidControlButton(
        modifier = Modifier.radialPosition(120f - 30f * position),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_SELECT),
        icon = R.drawable.button_select,
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonL1() {
    OmnidroidControlButton(
        modifier = Modifier.radialPosition(90f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_L1),
        label = "L1",
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonL2() {
    OmnidroidControlButton(
        modifier = Modifier.radialPosition(120f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_L2),
        label = "L2",
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonR1() {
    OmnidroidControlButton(
        modifier = Modifier.radialPosition(90f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_R1),
        label = "R1",
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonR2() {
    OmnidroidControlButton(
        modifier = Modifier.radialPosition(60f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_R2),
        label = "R2",
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonL() {
    OmnidroidControlButton(
        modifier = Modifier.radialPosition(120f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_L1),
        label = "L",
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonR() {
    OmnidroidControlButton(
        modifier = Modifier.radialPosition(60f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_R1),
        label = "R",
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonStart(position: Int = 0) {
    OmnidroidControlButton(
        modifier = Modifier.radialPosition(60f + 30f * position),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_START),
        icon = R.drawable.button_start,
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonMenu(settings: TouchControllerSettingsManager.Settings) {
    OmnidroidControlButton(
        modifier = Modifier.radialPosition(-60f + 2f * settings.rotation * TouchControllerSettingsManager.MAX_ROTATION),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_MODE),
        icon = R.drawable.button_menu,
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonMenuPlaceholder(settings: TouchControllerSettingsManager.Settings) {
    Box(
        modifier =
            Modifier.radialPosition(
                -120f - 2f * settings.rotation * TouchControllerSettingsManager.MAX_ROTATION,
            ),
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryAnalogLeft() {
    OmnidroidControlAnalog(
        modifier =
            Modifier
                .radialPosition(-80f)
                .radialScale(2.0f),
        id = Id.ContinuousDirection(ComposeTouchLayouts.MOTION_SOURCE_LEFT_STICK),
        analogPressId = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBL),
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryAnalogRight() {
    OmnidroidControlAnalog(
        modifier =
            Modifier
                .radialPosition(+80f - 180f)
                .radialScale(2.0f),
        id = Id.ContinuousDirection(ComposeTouchLayouts.MOTION_SOURCE_RIGHT_STICK),
        analogPressId = Id.Key(KeyEvent.KEYCODE_BUTTON_THUMBR),
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonCoin() {
    OmnidroidControlButton(
        modifier = Modifier.radialPosition(120f),
        id = Id.Key(KeyEvent.KEYCODE_BUTTON_SELECT),
        icon = R.drawable.button_coin,
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonScreenLayout(positionDegrees: Float = -120f) {
    OmnidroidControlButton(
        modifier = Modifier.radialPosition(positionDegrees),
        id = Id.Key(ComposeTouchLayouts.HOST_KEY_SCREEN_LAYOUT),
        icon = R.drawable.button_screen_layout,
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonHidePads(positionDegrees: Float = -90f) {
    OmnidroidControlButton(
        modifier = Modifier.radialPosition(positionDegrees),
        id = Id.Key(ComposeTouchLayouts.HOST_KEY_HIDE_PADS),
        icon = R.drawable.button_hide_pads,
    )
}

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun SecondaryButtonCloseLid(positionDegrees: Float = -60f) {
    OmnidroidControlButton(
        modifier = Modifier.radialPosition(positionDegrees),
        id = Id.Key(ComposeTouchLayouts.HOST_KEY_CLOSE_LID),
        icon = R.drawable.button_close_screen,
    )
}

object ComposeTouchLayouts {
    const val MOTION_SOURCE_DPAD = 0
    const val MOTION_SOURCE_LEFT_STICK = 1
    const val MOTION_SOURCE_RIGHT_STICK = 2
    const val MOTION_SOURCE_DPAD_AND_LEFT_STICK = 3
    const val MOTION_SOURCE_RIGHT_DPAD = 4

    /** Host-only: toggle DS/3DS dual ↔ top-only layout (not sent to core). */
    const val HOST_KEY_SCREEN_LAYOUT = KeyEvent.KEYCODE_BUTTON_1

    /** Host-only: slide virtual pads away / restore (not sent to core). */
    const val HOST_KEY_HIDE_PADS = KeyEvent.KEYCODE_BUTTON_2

    /** Host-only: close/open DS lid across different core mappings. */
    const val HOST_KEY_CLOSE_LID = KeyEvent.KEYCODE_BUTTON_3
}
