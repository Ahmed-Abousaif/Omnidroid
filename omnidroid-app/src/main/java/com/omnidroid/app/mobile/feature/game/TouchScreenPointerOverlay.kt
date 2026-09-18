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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import com.swordfish.libretrodroid.GLRetroView

/**
 * Captures touches on the emulated touchscreen region and forwards them to [GLRetroView].
 *
 * LibretroDroid only handles the primary pointer in [GLRetroView.onTouchEvent]. When a finger is
 * already holding a virtual button, a second finger arrives as a secondary pointer and is ignored.
 * This overlay lives in Compose (where each new pointer is hit-tested independently) and injects
 * synthetic single-pointer events so the DS/3DS stylus keeps working while buttons are held.
 *
 * @param retroViewBoundsInRoot Bounds of the full-screen [GLRetroView] in Compose root coordinates.
 * @param touchScreenBoundsInRoot Bounds of the game image / touchscreen area in root coordinates.
 */
@Composable
fun TouchScreenPointerOverlay(
    enabled: Boolean,
    retroView: GLRetroView?,
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
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                var stylusPointerId: PointerId? = null

                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val view = currentRetroView.value ?: continue
                                    val retroBounds = currentRetroBounds.value ?: continue
                                    val touchBounds = currentTouchBounds.value ?: continue

                                    event.changes.forEach { change ->
                                        val rootPosition =
                                            Offset(
                                                touchBounds.left + change.position.x,
                                                touchBounds.top + change.position.y,
                                            )

                                        when {
                                            stylusPointerId == null &&
                                                change.pressed &&
                                                !change.previousPressed -> {
                                                if (!touchBounds.contains(rootPosition)) {
                                                    return@forEach
                                                }
                                                stylusPointerId = change.id
                                                change.consume()
                                                forwardTouch(
                                                    retroView = view,
                                                    retroViewBoundsInRoot = retroBounds,
                                                    rootPosition = rootPosition,
                                                    action = MotionEvent.ACTION_DOWN,
                                                )
                                            }

                                            change.id == stylusPointerId && change.pressed -> {
                                                change.consume()
                                                forwardTouch(
                                                    retroView = view,
                                                    retroViewBoundsInRoot = retroBounds,
                                                    rootPosition = rootPosition,
                                                    action = MotionEvent.ACTION_MOVE,
                                                )
                                            }

                                            change.id == stylusPointerId && !change.pressed -> {
                                                change.consume()
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
                        }
                    } else {
                        Modifier
                    },
                ),
    )
}

private fun forwardTouch(
    retroView: GLRetroView,
    retroViewBoundsInRoot: Rect,
    rootPosition: Offset,
    action: Int,
) {
    val viewWidth = retroView.width
    val viewHeight = retroView.height
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
