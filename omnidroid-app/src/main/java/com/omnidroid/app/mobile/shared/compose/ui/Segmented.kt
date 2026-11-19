package com.omnidroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnidroid.app.mobile.shared.controller.controllerFocusGlow

/**
 * A pill of mutually exclusive segments, for a choice short enough to show all at once: one tap
 * picks a value, no dialog. The selected segment is filled with the accent; [content] draws each
 * segment's label in the colour it is given. Each segment is focusable, so the D-pad moves between
 * them.
 */
@Composable
fun <T> SegmentedPills(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    segmentHeight: Dp = 32.dp,
    segmentPadding: Dp = 14.dp,
    enabled: Boolean = true,
    content: @Composable (option: T, contentColor: Color) -> Unit,
) {
    val accent = libraryAccentColor
    val onAccent = onLibraryAccentColor
    Row(
        modifier =
            modifier
                .border(BorderStroke(1.dp, chromeColors.panelBorder), CircleShape)
                .padding(3.dp)
                .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier =
                    Modifier
                        .minimumInteractiveComponentSize()
                        .height(segmentHeight)
                        .controllerFocusGlow(
                            shape = CircleShape,
                            glowColor = if (isSelected) onAccent else accent,
                        )
                        .background(if (isSelected) accent else Color.Transparent, CircleShape)
                        .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton) {
                            onSelect(option)
                        }
                        .padding(horizontal = segmentPadding),
                contentAlignment = Alignment.Center,
            ) {
                content(option, if (isSelected) onAccent else chromeColors.content)
            }
        }
    }
}
