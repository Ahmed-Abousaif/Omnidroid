package com.omnidroid.app.testing

import android.view.ViewGroup
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogHandle
import com.omnidroid.app.mobile.shared.compose.dialog.showOmnidroidDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class PickerDialogHostTest {
    @get:Rule
    val compose = createAndroidComposeRule<LibraryStressActivity>()

    @Test
    fun migrationDialogWorksWithoutActivityContent() {
        var handle: OmnidroidDialogHandle? = null
        var dismissals = 0
        // Like SaveFrameworkPickerLauncher, this activity has never set a content view.
        compose.activityRule.scenario.onActivity { activity ->
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            assertEquals(0, content.childCount)
            handle =
                showOmnidroidDialog(
                    activity = activity,
                    title = "Migrating saves",
                    message = "Moving saves to the selected folder.",
                    actions = emptyList(),
                    cancelable = false,
                    onDismiss = { dismissals++ },
                )
            assertNotNull(handle)
        }

        compose.onNodeWithText("Migrating saves").assertIsDisplayed()
        compose.onNodeWithTag("omnidroid-dialog").assertIsDisplayed()
        compose.runOnIdle {
            handle?.dismiss()
            handle?.dismiss()
        }
        compose.waitUntil { dismissals == 1 }
        compose.onNodeWithTag("omnidroid-dialog").assertDoesNotExist()
        compose.activityRule.scenario.onActivity { activity ->
            assertEquals(0, activity.findViewById<ViewGroup>(android.R.id.content).childCount)
        }
    }
}
