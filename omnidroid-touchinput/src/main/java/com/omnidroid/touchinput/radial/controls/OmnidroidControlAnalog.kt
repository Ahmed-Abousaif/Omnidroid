package com.omnidroid.touchinput.radial.controls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.omnidroid.touchinput.radial.LocalOmnidroidPadTheme
import com.omnidroid.touchinput.radial.ui.OmnidroidButtonForeground
import com.omnidroid.touchinput.radial.ui.OmnidroidControlBackground
import gg.padkit.PadKitScope
import gg.padkit.controls.ControlAnalog
import gg.padkit.ids.Id

context(PadKitScope)
@Composable
fun OmnidroidControlAnalog(
    modifier: Modifier = Modifier,
    analogPressId: Id.Key? = null,
    id: Id.ContinuousDirection,
) {
    val theme = LocalOmnidroidPadTheme.current
    Box(
        modifier = modifier.padding(theme.padding),
        contentAlignment = Alignment.Center,
    ) {
        ControlAnalog(
            id = id,
            analogPressId = analogPressId,
            background = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { OmnidroidControlBackground(Modifier.fillMaxSize(0.8f)) }
            },
            foreground = { OmnidroidButtonForeground(pressed = it) },
        )
    }
}
