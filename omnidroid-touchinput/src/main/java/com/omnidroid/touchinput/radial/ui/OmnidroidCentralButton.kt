package com.omnidroid.touchinput.radial.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.omnidroid.touchinput.radial.LocalOmnidroidPadTheme

@Composable
fun OmnidroidCentralButton(
    pressedState: State<Boolean>,
    label: String? = null,
) {
    val theme = LocalOmnidroidPadTheme.current
    Box(modifier = Modifier.padding(theme.padding)) {
        OmnidroidControlBackground()
        OmnidroidButtonForeground(
            pressed = pressedState,
            label = label,
        )
    }
}
