package com.omnidroid.touchinput.radial.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.omnidroid.touchinput.radial.LocalOmnidroidPadTheme

@Composable
fun OmnidroidControlBackground(modifier: Modifier = Modifier) {
    val theme = LocalOmnidroidPadTheme.current
    GlassSurface(
        modifier = modifier.fillMaxSize(),
        fillColor = theme.level1Fill,
        shadowColor = theme.level1Shadow,
        shadowWidth = theme.level1ShadowWidth,
    )
}
