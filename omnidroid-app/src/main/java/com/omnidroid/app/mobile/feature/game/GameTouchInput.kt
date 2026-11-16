package com.omnidroid.app.mobile.feature.game

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import com.omnidroid.app.shared.game.view.IRetroGameView
import gg.padkit.UnhandledPointer

/** PadKit routes individual fingers here after testing the actual control hit regions. */
@Composable
internal fun rememberGameTouchInput(
    enabled: Boolean,
    retroView: IRetroGameView?,
    retroBounds: Rect?,
    touchBounds: Rect?,
): ((List<UnhandledPointer>) -> Unit)? {
    if (!enabled || retroView == null || retroBounds == null || touchBounds == null) return null
    val router = remember(retroView, retroBounds, touchBounds) { StylusPointerRouter(touchBounds) }
    DisposableEffect(router) {
        onDispose { router.cancel()?.let { forwardTouch(retroView, retroBounds, it) } }
    }
    return remember(router) {
        { pointers -> router.update(pointers).forEach { forwardTouch(retroView, retroBounds, it) } }
    }
}

private fun forwardTouch(retroView: IRetroGameView, bounds: Rect, event: StylusPointerRouter.Event) {
    val view = retroView.asView()
    if (view.width <= 0 || view.height <= 0 || bounds.width <= 0f || bounds.height <= 0f) return
    val action = when (event.action) {
        StylusPointerRouter.Action.DOWN -> MotionEvent.ACTION_DOWN
        StylusPointerRouter.Action.MOVE -> MotionEvent.ACTION_MOVE
        StylusPointerRouter.Action.UP -> MotionEvent.ACTION_UP
    }
    val now = SystemClock.uptimeMillis()
    val motion = MotionEvent.obtain(
        now, now, action,
        (event.position.x - bounds.left) * view.width / bounds.width,
        (event.position.y - bounds.top) * view.height / bounds.height,
        0,
    )
    try {
        retroView.onTouchEvent(motion)
    } finally {
        motion.recycle()
    }
}
