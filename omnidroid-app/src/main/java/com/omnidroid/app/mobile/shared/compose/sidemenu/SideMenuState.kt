package com.omnidroid.app.mobile.shared.compose.sidemenu

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Accept one completion per opening, including a close requested before the first frame. */
internal class SideMenuCloseGate {
    private var closing = false
    private var completed = false
    private var completion: () -> Unit = {}

    fun request(block: () -> Unit): Boolean {
        if (closing || completed) return false
        closing = true
        completion = block
        return true
    }

    fun complete(onClosed: () -> Unit) {
        if (!closing || completed) return
        completed = true
        val action = completion
        completion = {}
        onClosed()
        action()
    }
}

class SideMenuState internal constructor() {
    val transition = MutableTransitionState(false).apply { targetState = true }
    private val closeGate = SideMenuCloseGate()

    fun closeThen(action: () -> Unit = {}) {
        if (closeGate.request(action)) transition.targetState = false
    }

    internal fun completeClose(onClosed: () -> Unit) = closeGate.complete(onClosed)
}

@Composable
fun rememberSideMenuState(): SideMenuState = remember { SideMenuState() }

internal fun sideMenuWidth(available: Float): Float =
    (available * 0.42f).coerceIn(320f, 440f).coerceAtMost(available.coerceAtLeast(0f))
