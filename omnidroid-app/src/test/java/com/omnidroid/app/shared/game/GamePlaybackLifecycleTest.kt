package com.omnidroid.app.shared.game

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class GamePlaybackLifecycleTest {
    private class Host : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }

    @Test
    fun returningFromAnotherAppStaysPausedUntilMenuIsDismissed() {
        val host = Host()
        val playback = GamePlaybackLifecycle(host.lifecycle)
        host.registry.currentState = Lifecycle.State.RESUMED
        assertEquals(Lifecycle.State.RESUMED, playback.lifecycle.currentState)

        host.registry.currentState = Lifecycle.State.STARTED
        playback.onBackground()
        host.registry.currentState = Lifecycle.State.CREATED
        host.registry.currentState = Lifecycle.State.RESUMED
        assertTrue(playback.menuRequired)
        assertEquals(Lifecycle.State.STARTED, playback.lifecycle.currentState)

        playback.onMenuOpened()
        host.registry.currentState = Lifecycle.State.STARTED
        playback.onBackground()
        host.registry.currentState = Lifecycle.State.RESUMED
        assertFalse(playback.menuRequired)
        assertEquals(Lifecycle.State.STARTED, playback.lifecycle.currentState)

        playback.onMenuClosed()
        assertEquals(Lifecycle.State.RESUMED, playback.lifecycle.currentState)
    }

    @Test
    fun existingMenuSurvivesBackgroundingWithoutRequestingAnother() {
        val host = Host()
        val playback = GamePlaybackLifecycle(host.lifecycle)
        host.registry.currentState = Lifecycle.State.RESUMED
        playback.onMenuOpened()
        playback.onBackground()
        host.registry.currentState = Lifecycle.State.CREATED
        host.registry.currentState = Lifecycle.State.STARTED
        assertTrue(playback.menuOpen)
        assertFalse(playback.menuRequired)
        playback.onMenuClosed()
        assertEquals(Lifecycle.State.STARTED, playback.lifecycle.currentState)
        host.registry.currentState = Lifecycle.State.RESUMED
        assertEquals(Lifecycle.State.RESUMED, playback.lifecycle.currentState)
    }

    @Test
    fun loadingAndRecreationWhileBackgroundedDoNotResumePlayback() {
        val host = Host()
        host.registry.currentState = Lifecycle.State.CREATED
        val playback = GamePlaybackLifecycle(host.lifecycle, menuRequired = true)
        host.registry.currentState = Lifecycle.State.RESUMED
        assertEquals(Lifecycle.State.STARTED, playback.lifecycle.currentState)
        host.registry.currentState = Lifecycle.State.DESTROYED
        assertEquals(Lifecycle.State.DESTROYED, playback.lifecycle.currentState)
    }
}
