package com.omnidroid.app.shared.game

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/** Keeps the emulator paused while the activity returns and opens its pause menu. */
class GamePlaybackLifecycle(
    private val host: Lifecycle,
    menuRequired: Boolean = false,
    menuOpen: Boolean = false,
) : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = registry

    var menuRequired: Boolean = menuRequired
        private set
    var menuOpen: Boolean = menuOpen
        private set

    init {
        host.addObserver(LifecycleEventObserver { _, _ -> updateState() })
        updateState()
    }

    fun onBackground() {
        if (!menuOpen) menuRequired = true
        updateState()
    }

    fun onMenuOpened() {
        menuOpen = true
        menuRequired = false
        updateState()
    }

    fun onMenuClosed() {
        menuOpen = false
        updateState()
    }

    private fun updateState() {
        registry.currentState =
            if (menuRequired || menuOpen) {
                minOf(host.currentState, Lifecycle.State.STARTED)
            } else {
                host.currentState
            }
    }
}
