package com.omnidroid.app.testing

import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omnidroid.R
import com.omnidroid.app.shared.ps3.Ps3GuestDialogs
import com.omnidroid.app.shared.ps3.Ps3HostCallbacks
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Tests the host dialog protocol independently of the game's rendering timing. */
@RunWith(AndroidJUnit4::class)
class Ps3GuestDialogsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun node(text: String) = node { it.text?.toString() == text }
    private fun node(matches: (android.view.accessibility.AccessibilityNodeInfo) -> Boolean): android.view.accessibility.AccessibilityNodeInfo {
        fun find(root: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
            if (root == null) return null
            if (root.isVisibleToUser && matches(root)) return root
            for (index in 0 until root.childCount) find(root.getChild(index))?.let { return it }
            return null
        }
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            find(instrumentation.uiAutomation.rootInActiveWindow)?.let { return it }
            Thread.sleep(50)
        }
        error("Missing guest dialog node")
    }
    private fun click(text: String) {
        val bounds = android.graphics.Rect().apply { node(text).getBoundsInScreen(this) }
        val down = android.os.SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
    }
    @Test fun ownsMessageKeyboardAndSaveDataRequests() {
        data class Reply(val token: Long, val result: Int, val text: String)
        val replies = LinkedBlockingQueue<Reply>()
        ActivityScenario.launch(LibraryStressActivity::class.java).use { scenario ->
            lateinit var dialogs: Ps3GuestDialogs
            scenario.onActivity { activity ->
                dialogs = Ps3GuestDialogs.attach(activity) { token, result, text ->
                    replies.add(Reply(token, result, text)); true
                }
            }
            try {
                Ps3HostCallbacks.onDialog(1001, 1, """{"title":"Guest message","message":"Continue the game?","buttons":1,"cancelable":false}""")
                node("Continue the game?")
                click(instrumentation.targetContext.getString(R.string.ps3_guest_yes))
                assertEquals(Reply(1001, 1, ""), replies.poll(10, TimeUnit.SECONDS))

                Ps3HostCallbacks.onDialog(1002, 2, """{"title":"World name","initial":"Old","maxLength":32,"prohibitFlags":2}""")
                node("World name")
                val text = android.os.Bundle().apply {
                    putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Omni 😀")
                }
                assertTrue(node { it.isEditable }.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, text))
                click(instrumentation.targetContext.getString(R.string.game_keyboard_done))
                assertEquals(Reply(1002, 0, "Omni 😀"), replies.poll(10, TimeUnit.SECONDS))

                Ps3HostCallbacks.onDialog(1003, 3, """{"allowNew":true,"entries":[{"title":"Existing save","subtitle":"Level 1"}]}""")
                node("Existing save\nLevel 1")
                click(instrumentation.targetContext.getString(R.string.ps3_guest_new_savedata))
                assertEquals(Reply(1003, -1, ""), replies.poll(10, TimeUnit.SECONDS))

                // Engine-originated close must dismiss without manufacturing a user reply.
                Ps3HostCallbacks.onDialog(1004, 1, """{"message":"Waiting for the game","buttons":0}""")
                node("Waiting for the game")
                Ps3HostCallbacks.onDialog(1004, 5, "{}")
                instrumentation.waitForIdleSync()
                assertNull(replies.poll(300, TimeUnit.MILLISECONDS))
            } finally {
                scenario.onActivity { dialogs.detach() }
            }
        }
    }
}
