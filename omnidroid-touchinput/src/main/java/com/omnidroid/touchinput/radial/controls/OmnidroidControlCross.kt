package com.omnidroid.touchinput.radial.controls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.omnidroid.touchinput.radial.LocalOmnidroidPadTheme
import com.omnidroid.touchinput.radial.settings.TouchControllerSettingsManager
import com.omnidroid.touchinput.radial.ui.OmnidroidControlBackground
import com.omnidroid.touchinput.radial.ui.OmnidroidCrossForeground
import gg.padkit.PadKitScope
import gg.padkit.controls.ControlCross
import gg.padkit.ids.Id

context(PadKitScope)
@Composable
fun OmnidroidControlCross(
    modifier: Modifier = Modifier,
    id: Id.DiscreteDirection,
    allowDiagonals: Boolean = true,
    dpadStyle: TouchControllerSettingsManager.DpadStyle = TouchControllerSettingsManager.DpadStyle.CROSS,
    background: @Composable () -> Unit = {
        OmnidroidControlBackground()
    },
    foreground: @Composable (State<Offset>) -> Unit = {
        OmnidroidCrossForeground(
            allowDiagonals = allowDiagonals,
            directionState = it,
        )
    },
) {
    val theme = LocalOmnidroidPadTheme.current
    when (dpadStyle) {
        TouchControllerSettingsManager.DpadStyle.CROSS -> {
            ControlCross(
                modifier = modifier.padding(theme.padding),
                id = id,
                allowDiagonals = allowDiagonals,
                background = background,
                foreground = foreground,
            )
        }
        TouchControllerSettingsManager.DpadStyle.ANALOG -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                OmnidroidControlAnalog(
                    modifier = Modifier.fillMaxSize(0.85f),
                    id = Id.ContinuousDirection(id.value),
                )
            }
        }
    }
}

