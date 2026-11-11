package com.omnidroid.app.mobile.feature.game

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import com.omnidroid.app.shared.game.view.IRetroGameView

/**
 * Captures touches on the emulated touchscreen region and forwards them to [IRetroGameView].
 *
 * LibretroDroid only handles the primary pointer in [GLRetroView.onTouchEvent]. When a finger is
 * already holding a virtual button, a second finger arrives as a secondary pointer and is ignored.
 *
 * This overlay must be placed **outside** PadKit as a sibling sized to the game viewport only.
 * Nesting a consuming `pointerInput` inside PadKit breaks simultaneous virtual-button presses.
 *
 * Callers must not mount this above PadKit when virtual pads are drawn over the same viewport
 * (landscape overlay pads, e.g. Switch); Compose hit-tests the top sibling first and those
 * pad presses would never reach PadKit.
 *
 * @param retroViewBoundsInRoot Bounds of the full-screen [IRetroGameView] in Compose root coordinates.
 * @param touchScreenBoundsInRoot Bounds of this overlay / game viewport in root coordinates.
 */
@Composable
fun TouchScreenPointerOverlay(
    enabled: Boolean,
    retroView: IRetroGameView?,
    retroViewBoundsInRoot: Rect?,
    touchScreenBoundsInRoot: Rect?,
    modifier: Modifier = Modifier,
) {
    val currentRetroView = rememberUpdatedState(retroView)
    val currentRetroBounds = rememberUpdatedState(retroViewBoundsInRoot)
    val currentTouchBounds = rememberUpdatedState(touchScreenBoundsInRoot)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .then(
                    if (enabled) {
                        Modifier.pointerInput(touchScreenBoundsInRoot) {
                            var stylusPointerId: PointerId? = null
                            try {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val view = currentRetroView.value ?: continue
                                        val retroBounds = currentRetroBounds.value ?: continue
                                        val touchBounds = currentTouchBounds.value ?: continue

                                        event.changes.forEach { change ->
                                            // Local to this overlay, which is positioned on the viewport.
                                            val rootPosition =
                                                Offset(
                                                    touchBounds.left + change.position.x,
                                                    touchBounds.top + change.position.y,
                                                )

                                            when {
                                                stylusPointerId == null &&
                                                    change.pressed &&
                                                    !change.previousPressed -> {
                                                    stylusPointerId = change.id
                                                    // Do not consume: PadKit is a sibling, not a parent,
                                                    // and consuming is unnecessary for viewport-only hits.
                                                    forwardTouch(
                                                        retroView = view,
                                                        retroViewBoundsInRoot = retroBounds,
                                                        rootPosition = rootPosition,
                                                        action = MotionEvent.ACTION_DOWN,
                                                    )
                                                }

                                                change.id == stylusPointerId && change.pressed -> {
                                                    forwardTouch(
                                                        retroView = view,
                                                        retroViewBoundsInRoot = retroBounds,
                                                        rootPosition = rootPosition,
                                                        action = MotionEvent.ACTION_MOVE,
                                                    )
                                                }

                                                change.id == stylusPointerId && !change.pressed -> {
                                                    forwardTouch(
                                                        retroView = view,
                                                        retroViewBoundsInRoot = retroBounds,
                                                        rootPosition = rootPosition,
                                                        action = MotionEvent.ACTION_UP,
                                                    )
                                                    stylusPointerId = null
                                                }
                                            }
                                        }
                                    }
                                }
                            } finally {
                                // Layout changes and hiding the GamePad cancel this coroutine.
                                // Release the emulated pen even when no ACTION_UP arrives.
                                val view = currentRetroView.value
                                val bounds = currentRetroBounds.value
                                if (stylusPointerId != null && view != null && bounds != null) {
                                    forwardTouch(view, bounds, Offset.Zero, MotionEvent.ACTION_UP)
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
    )
}

private fun forwardTouch(
    retroView: IRetroGameView,
    retroViewBoundsInRoot: Rect,
    rootPosition: Offset,
    action: Int,
) {
    val view = retroView.asView()
    val viewWidth = view.width
    val viewHeight = view.height
    if (viewWidth <= 0 || viewHeight <= 0 ||
        retroViewBoundsInRoot.width == 0f ||
        retroViewBoundsInRoot.height == 0f
    ) {
        return
    }

    val localX =
        (rootPosition.x - retroViewBoundsInRoot.left) *
            (viewWidth / retroViewBoundsInRoot.width)
    val localY =
        (rootPosition.y - retroViewBoundsInRoot.top) *
            (viewHeight / retroViewBoundsInRoot.height)

    val now = SystemClock.uptimeMillis()
    val motionEvent = MotionEvent.obtain(now, now, action, localX, localY, 0)
    try {
        retroView.onTouchEvent(motionEvent)
    } finally {
        motionEvent.recycle()
    }
}
