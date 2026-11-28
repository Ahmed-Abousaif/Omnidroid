package com.omnidroid.app.shared.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameProcessLockTest {
    @Test
    fun cleanupTargetsOnlyAnotherGameProcessOwnedByThisApp() {
        val name = "com.omnidroid:game"
        assertTrue(GameProcessLock.isGameProcess(name, 1000, 20, name, 1000, 10))
        assertFalse(GameProcessLock.isGameProcess("com.omnidroid", 1000, 20, name, 1000, 10))
        assertFalse(GameProcessLock.isGameProcess("com.other:game", 1000, 20, name, 1000, 10))
        assertFalse(GameProcessLock.isGameProcess(name, 2000, 20, name, 1000, 10))
        assertFalse(GameProcessLock.isGameProcess(name, 1000, 10, name, 1000, 10))
    }
}
