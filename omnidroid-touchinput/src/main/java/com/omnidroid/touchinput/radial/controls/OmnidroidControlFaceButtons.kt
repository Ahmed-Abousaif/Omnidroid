package com.omnidroid.touchinput.radial.controls

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.omnidroid.touchinput.radial.LocalOmnidroidPadTheme
import com.omnidroid.touchinput.radial.ui.OmnidroidCompositeForeground
import com.omnidroid.touchinput.radial.ui.OmnidroidControlBackground
import gg.padkit.PadKitScope
import gg.padkit.anchors.Anchor
import gg.padkit.controls.ControlFaceButtons
import gg.padkit.ids.Id
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf

context(PadKitScope)
@Composable
fun OmnidroidControlFaceButtons(
    modifier: Modifier = Modifier,
    rotationInDegrees: Float = 0f,
    ids: PersistentList<Id.Key>,
    includeComposite: Boolean = true,
    background: @Composable () -> Unit = { OmnidroidControlBackground() },
    idsForegrounds: PersistentMap<Id.Key, @Composable (State<Boolean>) -> Unit>,
) {
    val theme = LocalOmnidroidPadTheme.current
    ControlFaceButtons(
        modifier = modifier.padding(theme.padding),
        includeComposite = includeComposite,
        ids = ids,
        trackPointers = true,
        rotationInDegrees = rotationInDegrees,
        foreground = { id, pressed -> (idsForegrounds[id]!!)(pressed) },
        background = background,
        foregroundComposite = { OmnidroidCompositeForeground(it) },
    )
}

context(PadKitScope)
@Composable
fun OmnidroidControlFaceButtons(
    modifier: Modifier = Modifier,
    primaryAnchors: PersistentList<Anchor<Id.Key>>,
    background: @Composable () -> Unit = { OmnidroidControlBackground() },
    idsForegrounds: PersistentMap<Id.Key, @Composable (State<Boolean>) -> Unit>,
) {
    ControlFaceButtons(
        modifier = modifier,
        primaryAnchors = primaryAnchors,
        compositeAnchors = persistentListOf(),
        trackPointers = false,
        foreground = { id, pressed -> (idsForegrounds[id]!!)(pressed) },
        background = background,
        foregroundComposite = { OmnidroidCompositeForeground(it) },
    )
}
