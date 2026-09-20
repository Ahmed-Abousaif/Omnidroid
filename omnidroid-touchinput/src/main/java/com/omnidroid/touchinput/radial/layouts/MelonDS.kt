package com.omnidroid.touchinput.radial.layouts

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.omnidroid.touchinput.R
import com.omnidroid.touchinput.radial.controls.OmnidroidControlButton
import com.omnidroid.touchinput.radial.controls.OmnidroidControlCross
import com.omnidroid.touchinput.radial.controls.OmnidroidControlFaceButtons
import com.omnidroid.touchinput.radial.layouts.shared.ComposeTouchLayouts
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonCloseLid
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonHidePads
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonL
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonMenu
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonMenuPlaceholder
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonR
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonScreenLayout
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonSelect
import com.omnidroid.touchinput.radial.layouts.shared.SecondaryButtonStart
import com.omnidroid.touchinput.radial.settings.TouchControllerSettingsManager
import com.omnidroid.touchinput.radial.ui.OmnidroidButtonForeground
import gg.padkit.PadKitScope
import gg.padkit.ids.Id
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@Composable
fun PadKitScope.MelonDSLeft(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutLeft(
        settings = settings,
        modifier = modifier,
        primaryDial = { OmnidroidControlCross(id = Id.DiscreteDirection(ComposeTouchLayouts.MOTION_SOURCE_DPAD), allowDiagonals = settings.allowDiagonals) },
        secondaryDials = {
            SecondaryButtonL()
            SecondaryButtonSelect(position = 2)
            SecondaryButtonMenuPlaceholder(settings)
            OmnidroidControlButton(
                modifier = Modifier.radialPosition(-120f),
                id = Id.Key(KeyEvent.KEYCODE_BUTTON_L2),
                icon = R.drawable.button_mic,
            )
            SecondaryButtonCloseLid(positionDegrees = -60f)
            Box(
                modifier = Modifier.radialPosition(-90f),
            )
        },
    )
}

@Composable
fun PadKitScope.MelonDSRight(
    modifier: Modifier = Modifier,
    settings: TouchControllerSettingsManager.Settings,
) {
    BaseLayoutRight(
        settings = settings,
        modifier = modifier,
        primaryDial = {
            OmnidroidControlFaceButtons(
                ids =
                    persistentListOf(
                        Id.Key(KeyEvent.KEYCODE_BUTTON_A),
                        Id.Key(KeyEvent.KEYCODE_BUTTON_B),
                        Id.Key(KeyEvent.KEYCODE_BUTTON_Y),
                        Id.Key(KeyEvent.KEYCODE_BUTTON_X),
                    ),
                idsForegrounds =
                    persistentMapOf<Id.Key, @Composable (State<Boolean>) -> Unit>(
                        Id.Key(KeyEvent.KEYCODE_BUTTON_A) to { OmnidroidButtonForeground(pressed = it, label = "A") },
                        Id.Key(KeyEvent.KEYCODE_BUTTON_B) to { OmnidroidButtonForeground(pressed = it, label = "B") },
                        Id.Key(KeyEvent.KEYCODE_BUTTON_Y) to { OmnidroidButtonForeground(pressed = it, label = "Y") },
                        Id.Key(KeyEvent.KEYCODE_BUTTON_X) to { OmnidroidButtonForeground(pressed = it, label = "X") },
                    ),
            )
        },
        secondaryDials = {
            SecondaryButtonR()
            SecondaryButtonStart(position = 2)
            SecondaryButtonScreenLayout(positionDegrees = -120f)
            SecondaryButtonHidePads(positionDegrees = -90f)
            SecondaryButtonMenu(settings)
        },
    )
}
