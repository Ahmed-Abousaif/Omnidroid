package com.omnidroid.touchinput.radial.controls

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.omnidroid.touchinput.radial.LocalOmnidroidPadTheme
import com.omnidroid.touchinput.radial.ui.OmnidroidButtonForeground
import com.omnidroid.touchinput.radial.ui.OmnidroidControlBackground
import gg.padkit.PadKitScope
import gg.padkit.controls.ControlButton
import gg.padkit.ids.Id
import gg.padkit.layouts.radial.secondarydials.LayoutRadialSecondaryDialsScope

context(PadKitScope, LayoutRadialSecondaryDialsScope)
@Composable
fun OmnidroidControlButton(
    modifier: Modifier = Modifier,
    id: Id.Key,
    label: String? = null,
    icon: Int? = null,
) {
    val theme = LocalOmnidroidPadTheme.current
    ControlButton(
        modifier = modifier.padding(theme.padding),
        id = id,
        foreground = { OmnidroidButtonForeground(pressed = it, icon = icon, label = label) },
        background = { OmnidroidControlBackground() },
    )
}
