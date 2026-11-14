package com.omnidroid.app.mobile.shared.compose.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.flowOf

internal data class DialogPaneInsets(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0)

/** Keep the complete panel on the larger unobstructed side of a separating hinge. */
internal fun dialogPaneInsets(width: Int, height: Int, hinge: IntRect?, vertical: Boolean): DialogPaneInsets {
    if (hinge == null || hinge.right <= 0 || hinge.left >= width || hinge.bottom <= 0 || hinge.top >= height) {
        return DialogPaneInsets()
    }
    return if (vertical) {
        val leftWidth = hinge.left.coerceIn(0, width)
        val rightStart = hinge.right.coerceIn(0, width)
        if (leftWidth >= width - rightStart) DialogPaneInsets(right = width - leftWidth)
        else DialogPaneInsets(left = rightStart)
    } else {
        val topHeight = hinge.top.coerceIn(0, height)
        val bottomStart = hinge.bottom.coerceIn(0, height)
        if (topHeight >= height - bottomStart) DialogPaneInsets(bottom = height - topHeight)
        else DialogPaneInsets(top = bottomStart)
    }
}

@Composable
internal fun rememberDialogPaneInsets(width: Dp, height: Dp, origin: IntOffset): DialogPaneInsets {
    val activity = LocalContext.current.dialogActivity()
    val updates = remember(activity) {
        if (activity == null) flowOf(WindowLayoutInfo(emptyList()))
        else WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
    }
    val info by updates.collectAsState(initial = WindowLayoutInfo(emptyList()))
    val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull { it.isSeparating }
    val activityOrigin = IntArray(2)
    activity?.window?.decorView?.getLocationOnScreen(activityOrigin)
    val bounds = fold?.bounds?.let {
        IntRect(
            it.left + activityOrigin[0] - origin.x, it.top + activityOrigin[1] - origin.y,
            it.right + activityOrigin[0] - origin.x, it.bottom + activityOrigin[1] - origin.y,
        )
    }
    return with(LocalDensity.current) {
        dialogPaneInsets(width.roundToPx(), height.roundToPx(), bounds, fold?.orientation == FoldingFeature.Orientation.VERTICAL)
    }
}
