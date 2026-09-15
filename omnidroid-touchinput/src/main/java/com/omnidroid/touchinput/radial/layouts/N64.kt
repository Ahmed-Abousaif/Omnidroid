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
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonL
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonMenu
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonMenuPlaceholder
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonR
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonStart
import com.omnidroid.touchinput.radial.settings.TouchControllerSettingsManager
import com.omnidroid.touchinput.radial.ui.OmnidroidButtonForeground
import com.omnidroid.touchinput.radial.ui.OmnidroidCrossForegroundAlternate
import gg.padkit.PadKitScope
import gg.padkit.ids.Id
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@Composable
fun PadKitScope.N64Left(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutLeft(
        settings = settings,
        modifier = modifier,
        primaryDial = { OmnidroidControlCross(id = Id.DiscreteDirection(ComposeTouchLayouts.MOTION_SOURCE_DPAD)) },
        secondaryDials = {
            SecondaryAnalogLeft()
            SecondaryButtonL()
            OmnidroidControlButton(
                modifier = Modifier.radialPosition(60f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_L2),
                label = "Z",
            )
            SecondaryButtonMenuPlaceholder(settings)
        },
    )
}

@Composable
fun PadKitScope.N64Right(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutRight(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            OmnidroidControlFaceButtons(
                rotationInDegrees = 90f,
                ids =
                    persistentListOf(
                        Id.Key(KeyEvent.KEYCODE_BUTTON_B),
                        Id.Key(KeyEvent.KEYCODE_BUTTON_Y),
                        Id.Key(KeyEvent.KEYCODE_BUTTON_L2),
                    ),
                idsForegrounds =
                    persistentMapOf<Id.Key, @Composable (State<Boolean>) -> Unit>(
                        Id.Key(KeyEvent.KEYCODE_BUTTON_B) to { OmnidroidButtonForeground(pressed = it, label = "A") },
                        Id.Key(KeyEvent.KEYCODE_BUTTON_Y) to { OmnidroidButtonForeground(pressed = it, label = "B") },
                        Id.Key(KeyEvent.KEYCODE_BUTTON_L2) to { OmnidroidButtonForeground(pressed = it, label = "Z") },
                    ),
            )
        },
        secondaryDials = {
            SecondaryButtonR()
            SecondaryButtonStart(position = 2)
            OmnidroidControlCross(
                modifier =
                    Modifier
                        .radialPosition(+80f - 180f)
                        .radialScale(2.0f),
                id = Id.DiscreteDirection(ComposeTouchLayouts.MOTION_SOURCE_RIGHT_STICK),
                allowDiagonals = false,
                foreground = { OmnidroidCrossForegroundAlternate(it) },
            )
            SecondaryButtonMenu(settings)
        },
    )
}
