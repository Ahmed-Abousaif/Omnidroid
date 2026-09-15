package com.omnidroid.touchinput.radial.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.omnidroid.touchinput.radial.LocalOmnidroidPadTheme

@Composable
fun OmnidroidCompositeForeground(pressed: State<Boolean>) {
    val theme = LocalOmnidroidPadTheme.current
    GlassSurface(
        modifier = Modifier.fillMaxSize(),
        fillColor = theme.compositeFill(pressed.value),
        shadowColor = theme.level2Shadow,
        shadowWidth = theme.level2ShadowWidth,
    )
}
