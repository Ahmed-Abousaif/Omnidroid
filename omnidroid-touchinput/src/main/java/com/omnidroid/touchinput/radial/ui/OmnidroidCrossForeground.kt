package com.omnidroid.touchinput.radial.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import com.omnidroid.touchinput.radial.LocalOmnidroidPadTheme
import gg.padkit.ui.DefaultCrossForeground

@Composable
fun OmnidroidCrossForeground(
    allowDiagonals: Boolean,
    directionState: State<Offset>,
) {
    DefaultCrossForeground(
        modifier = Modifier.fillMaxSize(),
        directionState = directionState,
        allowDiagonals = allowDiagonals,
        leftDial = {
            OmnidroidCrossButton(it, Icons.Default.KeyboardArrowLeft)
        },
        rightDial = {
            OmnidroidCrossButton(it, Icons.Default.KeyboardArrowRight)
        },
        topDial = {
            OmnidroidCrossButton(it, Icons.Default.KeyboardArrowUp)
        },
        bottomDial = {
            OmnidroidCrossButton(it, Icons.Default.KeyboardArrowDown)
        },
        foregroundComposite = {
            OmnidroidCompositeForeground(it)
        },
    )
}

@Composable
private fun OmnidroidCrossButton(
    pressedState: State<Boolean>,
    imageVector: ImageVector,
) {
    OmnidroidButtonForeground(
        pressed = pressedState,
        label = { },
        icon = {
            Icon(
                modifier = Modifier.size(maxWidth * 0.5f, maxHeight * 0.5f),
                imageVector = imageVector,
                contentDescription = "",
                tint = LocalOmnidroidPadTheme.current.icons(pressedState.value),
            )
        },
    )
}
