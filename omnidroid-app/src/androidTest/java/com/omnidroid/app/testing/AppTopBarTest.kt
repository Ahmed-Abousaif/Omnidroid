package com.omnidroid.app.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.AppTheme
import com.omnidroid.app.mobile.shared.compose.ui.AppTopBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppTopBarTest {
    @get:Rule
    val compose = createAndroidComposeRule<LibraryStressActivity>()

    @Test
    fun indicatorsMoveContinuouslyAndBackRestoresLibraryControls() {
        val library = mutableStateOf(true)
        var backClicks = 0
        val backLabel = compose.activity.getString(R.string.back)
        val indicatorLabel = compose.activity.getString(R.string.status_gamepad_connected)
        compose.setContent {
            AppTheme {
                AppTopBar(
                    gamepadConnected = true,
                    portrait = false,
                    controlsVisible = library.value,
                    onBackPressed = if (library.value) null else ({
                        backClicks++
                        library.value = true
                    }),
                    leadingContent = { Text("Library search") },
                    trailingContent = { Box(Modifier.width(150.dp)) { Text("Library actions") } },
                )
            }
        }
        compose.onNodeWithContentDescription(backLabel).assertDoesNotExist()
        val startX = compose.onNodeWithContentDescription(indicatorLabel).fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { library.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(100)
        val middleX = compose.onNodeWithContentDescription(indicatorLabel).fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.advanceTimeBy(400)
        val endX = compose.onNodeWithContentDescription(indicatorLabel).fetchSemanticsNode().boundsInRoot.left
        assertTrue("Indicators must slide toward the screen edge", startX < middleX && middleX < endX)
        compose.onNodeWithText("Library actions").assertDoesNotExist()
        compose.onNodeWithText("Library search").assertDoesNotExist()
        compose.onNodeWithContentDescription(backLabel).assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription(backLabel).assertDoesNotExist()
        compose.onNodeWithText("Library actions").assertIsDisplayed()
        compose.onNodeWithText("Library search").assertIsDisplayed()
        val restoredX = compose.onNodeWithContentDescription(indicatorLabel).fetchSemanticsNode().boundsInRoot.left
        assertEquals(startX, restoredX, 1f)
        assertEquals(1, backClicks)
    }
}
