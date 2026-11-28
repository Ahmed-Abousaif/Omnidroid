package com.omnidroid.touchinput.radial.layouts

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.omnidroid.touchinput.radial.controls.OmnidroidControlButton
import com.omnidroid.touchinput.radial.controls.OmnidroidControlCross
import com.omnidroid.touchinput.radial.controls.OmnidroidControlFaceButtons
import com.omnidroid.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryAnalogLeft
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonMenu
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonSelect
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonStart
import com.omnidroid.touchinput.radial.settings.TouchControllerID
import com.omnidroid.touchinput.radial.settings.TouchControllerSettingsManager
import com.omnidroid.touchinput.radial.ui.OmnidroidButtonForeground
import gg.padkit.PadKitScope
import gg.padkit.ids.Id
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap

private data class PadLabels(
    val a: String,
    val b: String,
    val x: String,
    val y: String,
    val l: String,
    val r: String,
)

private fun labels(id: TouchControllerID): PadLabels =
    when (id) {
        TouchControllerID.ATARI5200 -> PadLabels("Fire 1", "Fire 2", "#", "*", "9", "0")
        TouchControllerID.ATARI800 -> PadLabels("Fire 2", "Fire 1", "Enter", "Space", "Option", "Menu")
        TouchControllerID.SATURN -> PadLabels("B", "A", "Y", "X", "C", "Z")
        TouchControllerID.THREE_DO -> PadLabels("C", "B", "", "A", "L", "R")
        TouchControllerID.COLECOVISION -> PadLabels("Red", "Yellow", "2", "1", "3", "4")
        TouchControllerID.C64 -> PadLabels("Fire 2", "Fire", "Space", "Keybd", "L", "R")
        TouchControllerID.MSX -> PadLabels("Fire A", "Fire B", "F3", "Space", "F4", "F5")
        TouchControllerID.APPLE2 -> PadLabels("Button 0", "Button 1", "X", "Y", "Space", "Enter")
        TouchControllerID.JAGUAR -> PadLabels("A", "B", "0", "C", "1", "2")
        else -> PadLabels("A", "B", "X", "Y", "L", "R")
    }

@Composable
fun PadKitScope.LegacyPadLeft(
    id: TouchControllerID,
    modifier: Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    val text = labels(id)
    BaseLayoutLeft(
        modifier = modifier,
        settings = settings,
        primaryDial = {
            OmnidroidControlCross(
                id = Id.DiscreteDirection(ComposeTouchLayouts.MOTION_SOURCE_DPAD),
                allowDiagonals = settings.allowDiagonals,
                dpadStyle = settings.dpadStyle,
            )
        },
        secondaryDials = {
            OmnidroidControlButton(
                modifier = Modifier.radialPosition(90f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_L1),
                label = text.l,
            )
            OmnidroidControlButton(
                modifier = Modifier.radialPosition(120f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_L2),
                label =
                    if (id ==
                        TouchControllerID.SATURN
                    ) {
                        "L"
                    } else {
                        "L2"
                    },
            )
            SecondaryButtonSelect(position = 2)
            if (id == TouchControllerID.ATARI5200) SecondaryAnalogLeft()
        },
    )
}

@Composable
fun PadKitScope.LegacyPadRight(
    id: TouchControllerID,
    modifier: Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    val text = labels(id)
    val buttons =
        listOf(
            KeyEvent.KEYCODE_BUTTON_A to text.a,
            KeyEvent.KEYCODE_BUTTON_B to text.b,
            KeyEvent.KEYCODE_BUTTON_Y to text.y,
            KeyEvent.KEYCODE_BUTTON_X to text.x,
        ).filter { it.second.isNotEmpty() }
    BaseLayoutRight(
        modifier = modifier,
        settings = settings,
        primaryDial = {
            OmnidroidControlFaceButtons(
                ids = buttons.map { Id.Key(it.first) }.toPersistentList(),
                idsForegrounds =
                    buttons
                        .associate { (code, label) ->
                            Id.Key(code) to
                                @Composable { pressed: State<Boolean> ->
                                    OmnidroidButtonForeground(pressed = pressed, label = label)
                                }
                        }.toPersistentMap(),
            )
        },
        secondaryDials = {
            OmnidroidControlButton(
                modifier = Modifier.radialPosition(30f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_R1),
                label = text.r,
            )
            OmnidroidControlButton(
                modifier = Modifier.radialPosition(60f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_R2),
                label =
                    if (id ==
                        TouchControllerID.SATURN
                    ) {
                        "R"
                    } else {
                        "R2"
                    },
            )
            SecondaryButtonStart(position = 2)
            SecondaryButtonMenu(settings)
        },
    )
}
