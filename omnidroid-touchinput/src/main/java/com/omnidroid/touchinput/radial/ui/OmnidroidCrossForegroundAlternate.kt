package com.omnidroid.touchinput.radial.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.omnidroid.touchinput.R
import gg.padkit.ui.DefaultCrossForeground

@Composable
fun OmnidroidCrossForegroundAlternate(directionState: State<Offset>) {
    DefaultCrossForeground(
        modifier = Modifier.fillMaxSize(),
        directionState = directionState,
        allowDiagonals = false,
        leftDial = {
            OmnidroidButtonForeground(
                pressed = it,
                icon = R.drawable.direction_alt_foreground_left,
            )
        },
        rightDial = {
            OmnidroidButtonForeground(
                pressed = it,
                icon = R.drawable.direction_alt_foreground_right,
            )
        },
        topDial = {
            OmnidroidButtonForeground(
                pressed = it,
                icon = R.drawable.direction_alt_foreground_up,
            )
        },
        bottomDial = {
            OmnidroidButtonForeground(
                pressed = it,
                icon = R.drawable.direction_alt_foreground_down,
            )
        },
        foregroundComposite = {
            OmnidroidCompositeForeground(it)
        },
    )
}
