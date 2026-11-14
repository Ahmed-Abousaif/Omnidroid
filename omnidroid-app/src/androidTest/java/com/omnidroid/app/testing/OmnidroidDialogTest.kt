package com.omnidroid.app.testing

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.dialog.DialogActionRole
import com.omnidroid.app.mobile.shared.compose.dialog.DialogScrollInfo
import com.omnidroid.app.mobile.shared.compose.dialog.DialogWidth
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogAction
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogButton
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogLayout
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogStyle
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogTextField
import com.omnidroid.app.mobile.shared.compose.dialog.showOmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.ui.AppTheme
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsList
import com.omnidroid.app.utils.android.settings.OmnidroidSettingsListMultiSelect
import com.omnidroid.app.utils.android.settings.stringsSetPreferenceState
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OmnidroidDialogTest {
    @get:Rule val compose = createAndroidComposeRule<LibraryStressActivity>()

    private fun assertPanelInsideWindow() {
        val window = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("omnidroid-dialog").fetchSemanticsNode().boundsInRoot
        assertTrue("Dialog must stay inside its window: $panel in $window",
            panel.left >= window.left && panel.top >= window.top && panel.right <= window.right && panel.bottom <= window.bottom)
    }

    private fun assertInputFitsPanel() {
        val panel = compose.onNodeWithTag("omnidroid-dialog").fetchSemanticsNode().boundsInRoot
        val field = compose.onNodeWithTag("editor").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        val padding = OmnidroidDialogStyle.padding.value * density
        assertEquals(panel.left + padding, field.left, 1f)
        assertEquals(panel.right - padding, field.right, 1f)
        assertTrue(panel.width <= OmnidroidDialogStyle.inputWidth.value * density + 1f)
    }

    private fun screenshot(name: String, viewportTag: String? = null) {
        val node = if (viewportTag == null) compose.onNode(isDialog()) else compose.onNodeWithTag(viewportTag)
        val bitmap = node.captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir(null), "dialogs-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun revealAction(label: String) {
        val node = compose.onNodeWithText(label, substring = false)
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed()
    }

    @Test fun longContentScrollsAndActionsStayReachable() {
        lateinit var inputMode: InputModeManager
        compose.setContent {
            AppTheme(followUserPreference = false) {
                inputMode = LocalInputModeManager.current
                OmnidroidDialog(
                    title = { Text("Delete database?") }, onDismissRequest = {}, destructive = true,
                    text = { Column { repeat(100) { Text("Library entry $it") } } },
                    dismissButton = { OmnidroidDialogButton(onClick = {}) { Text("Cancel") } },
                    confirmButton = { OmnidroidDialogButton(onClick = {}) { Text("Delete database") } },
                )
            }
        }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithText("Cancel").assertIsFocused()
        compose.onNodeWithTag("omnidroid-dialog-body-more-below").assertIsDisplayed()
        compose.onNodeWithTag("omnidroid-dialog-body-scrollbar").assertIsDisplayed()
        screenshot("scroll-hint")
        compose.onNodeWithText("Library entry 99").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("omnidroid-dialog-body-more-below").assertDoesNotExist()
        compose.onNodeWithTag("omnidroid-dialog-body-scrollbar").assertIsDisplayed()
        revealAction("Delete database")
        assertPanelInsideWindow()
        screenshot("confirmation")
    }

    @Test fun largeRtlTextAndLongActionsFitCompactWindow() {
        compose.setContent {
            AppTheme(followUserPreference = false) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density * 1.5f, fontScale = 2f),
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                ) {
                    // Render the actual shell inside the overridden density. Android Dialog
                    // windows obtain density from their own platform configuration.
                    Box(Modifier.fillMaxSize().testTag("large-font-viewport")) {
                    OmnidroidDialogLayout(
                        title = { Text("A long translated title that must wrap without clipping") },
                        onDismissRequest = {}, text = { Text("Detailed instructions. ".repeat(60)) },
                        dismissButton = { OmnidroidDialogButton(onClick = {}) { Text("Cancel these changes") } },
                        confirmButton = { OmnidroidDialogButton(onClick = {}) { Text("Confirm and import the selected files") } },
                        modifier = Modifier, icon = null, destructive = false, width = DialogWidth.Standard,
                        scrollBody = true, dismissOnClickOutside = true, dismissOnBackPress = true, handleControllerBack = true,
                    )
                    }
                }
            }
        }
        revealAction("Confirm and import the selected files")
        val window = compose.onNodeWithTag("large-font-viewport").fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("omnidroid-dialog").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.left >= window.left && panel.top >= window.top && panel.right <= window.right && panel.bottom <= window.bottom)
        screenshot("large-rtl", "large-font-viewport")
    }

    @Test fun cancelDiscardsSelectionAndConfirmCommitsOnce() {
        var selected = 0
        var commits = 0
        compose.setContent {
            AppTheme(followUserPreference = false) {
                OmnidroidSettingsList(
                    title = { Text("Speed") }, items = listOf("Normal", "Fast"), selectedIndex = selected,
                    onItemSelected = { index, _ -> selected = index; commits++ },
                )
            }
        }
        compose.onNodeWithText("Speed").performClick()
        compose.onNodeWithText("Fast").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
        compose.runOnIdle { assertEquals(0, commits); assertEquals(0, selected) }
        compose.onNodeWithText("Speed").performClick()
        compose.onNodeWithText("Fast").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.ok)).performClick()
        compose.runOnIdle { assertEquals(1, commits); assertEquals(1, selected) }
    }

    @Test fun textInputAndKeyboardKeepActionsReachable() {
        compose.setContent {
            AppTheme(followUserPreference = false) {
                var value by remember { mutableStateOf("") }
                OmnidroidDialog(
                    title = { Text("Edit game name") }, onDismissRequest = {},
                    width = DialogWidth.Input,
                    text = { OmnidroidDialogTextField(value, { value = it }, Modifier.testTag("editor"), singleLine = true) },
                    dismissButton = { OmnidroidDialogButton(onClick = {}) { Text("Cancel") } },
                    confirmButton = { OmnidroidDialogButton(onClick = {}) { Text("Save") } },
                )
            }
        }
        assertInputFitsPanel()
        screenshot("input-width")
        compose.onNodeWithTag("editor").performClick().performTextInput("Custom title")
        revealAction("Save")
        assertPanelInsideWindow()
        assertInputFitsPanel()
        screenshot("keyboard")
    }

    @Test fun activityBridgeUsesSameShellAndDisposesOnCancel() {
        var dismissed = 0
        compose.setContent { AppTheme { Text("Background") } }
        compose.runOnIdle {
            showOmnidroidDialog(
                activity = compose.activity, title = "Missing system files", message = "Import required keys.",
                actions = listOf(OmnidroidDialogAction("Cancel", DialogActionRole.Cancel)),
                onDismiss = { dismissed++ },
            )
        }
        compose.onNodeWithText("Missing system files").assertIsDisplayed()
        assertPanelInsideWindow()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil { dismissed == 1 }
        compose.onNodeWithTag("omnidroid-dialog").assertDoesNotExist()
        compose.onNodeWithText("Background").assertIsDisplayed()
    }

    @Test fun squarePortraitAndVeryShortWindowsKeepAllActionsReachable() {
        var viewport by mutableStateOf(DpSize(280.dp, 280.dp))
        compose.setContent {
            AppTheme(followUserPreference = false) {
                Box(Modifier.size(viewport).testTag("viewport")) {
                    OmnidroidDialogLayout(
                        onDismissRequest = {}, modifier = Modifier,
                        title = { Text("A long title for a compact handheld screen") }, icon = null,
                        text = { Text("Scrollable body content. ".repeat(60)) },
                        confirmButton = { OmnidroidDialogButton(onClick = {}) { Text("Confirm these changes") } },
                        dismissButton = { OmnidroidDialogButton(onClick = {}) { Text("Cancel") } },
                        destructive = false, width = DialogWidth.Standard, scrollBody = true,
                        dismissOnClickOutside = true, dismissOnBackPress = true, handleControllerBack = true,
                    )
                }
            }
        }
        for (size in listOf(DpSize(280.dp, 280.dp), DpSize(220.dp, 360.dp), DpSize(320.dp, 140.dp), DpSize(160.dp, 100.dp))) {
            compose.runOnIdle { viewport = size }
            revealAction("Confirm these changes")
            val window = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
            val panel = compose.onNodeWithTag("omnidroid-dialog").fetchSemanticsNode().boundsInRoot
            assertTrue("$size: $panel inside $window",
                panel.left >= window.left && panel.top >= window.top && panel.right <= window.right && panel.bottom <= window.bottom)
        }
    }

    @Test fun multiSelectionCancelDiscardsAndConfirmPersists() {
        val prefs = SharedPreferencesHelper.getSharedPreferences(compose.activity)
        val key = "__dialog_multi_selection_test"
        var commits = 0
        prefs.edit().remove(key).commit()
        try {
            compose.setContent {
                AppTheme(followUserPreference = false) {
                    OmnidroidSettingsListMultiSelect(
                        title = { Text("Sync states") },
                        state = stringsSetPreferenceState(key, setOf("nes")),
                        entryValues = listOf("nes", "snes"), entries = listOf("NES", "SNES"),
                        confirmButton = "Apply", onItemsSelected = { commits++ },
                    )
                }
            }
            compose.onNodeWithText("Sync states").performClick()
            compose.onNodeWithText("SNES").performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
            compose.runOnIdle {
                assertEquals(0, commits)
                assertEquals(setOf("nes"), prefs.getStringSet(key, setOf("nes")))
            }
            compose.onNodeWithText("Sync states").performClick()
            compose.onNodeWithText("SNES").performClick()
            compose.onNodeWithText("Apply").performClick()
            compose.runOnIdle {
                assertEquals(1, commits)
                assertEquals(setOf("nes", "snes"), prefs.getStringSet(key, emptySet()))
            }
        } finally {
            prefs.edit().remove(key).commit()
        }
    }

    @Test fun lightThemeUsesSameShellWithReadableActions() {
        val prefs = SharedPreferencesHelper.getSharedPreferences(compose.activity)
        val key = compose.activity.getString(R.string.pref_key_app_theme)
        val previous = prefs.getString(key, null)
        prefs.edit().putString(key, "light").commit()
        try {
            compose.setContent {
                AppTheme {
                    OmnidroidDialog(
                        title = { Text("Save changes?") }, onDismissRequest = {},
                        text = { Text("The dialog follows the same palette as the side menu.") },
                        dismissButton = { OmnidroidDialogButton(onClick = {}) { Text("Cancel") } },
                        confirmButton = { OmnidroidDialogButton(onClick = {}) { Text("Save") } },
                    )
                }
            }
            revealAction("Save")
            assertPanelInsideWindow()
            screenshot("light")
        } finally {
            if (previous == null) prefs.edit().remove(key).commit()
            else prefs.edit().putString(key, previous).commit()
        }
    }

    @Test fun shortMessageDialogRespectsMinimumWidth() {
        compose.setContent {
            AppTheme(followUserPreference = false) {
                OmnidroidDialog(
                    title = { Text("Done") }, onDismissRequest = {}, text = { Text("Saved.") },
                    confirmButton = { OmnidroidDialogButton(onClick = {}) { Text("OK") } },
                )
            }
        }
        val panel = compose.onNodeWithTag("omnidroid-dialog").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertEquals(OmnidroidDialogStyle.minWidth.value * density, panel.width, 1f)
        assertTrue("Short content should not stretch to the standard dialog width",
            panel.width < OmnidroidDialogStyle.width.value * density)
        compose.onNodeWithText("OK").assertIsDisplayed()
        compose.onNodeWithTag("omnidroid-dialog-body-more-below").assertDoesNotExist()
        assertPanelInsideWindow()
        compose.onNodeWithTag("omnidroid-dialog-body-scrollbar").assertDoesNotExist()
        screenshot("compact-message")
    }
    @Test fun externallyScrolledContentUsesTheSharedIndicators() {
        var scrollInfo by mutableStateOf<DialogScrollInfo?>(DialogScrollInfo(0f, 0.25f, true))
        compose.setContent {
            AppTheme(followUserPreference = false) {
                OmnidroidDialog(
                    title = { Text("Artwork results") }, onDismissRequest = {},
                    width = DialogWidth.Standard, scrollBody = false, bodyScrollInfo = scrollInfo,
                    text = { Box(Modifier.fillMaxWidth().height(200.dp)) { Text("Cover grid") } },
                    confirmButton = { OmnidroidDialogButton(onClick = {}) { Text("Close") } },
                )
            }
        }
        compose.onNodeWithTag("omnidroid-dialog-body-more-below").assertIsDisplayed()
        compose.onNodeWithTag("omnidroid-dialog-body-scrollbar").assertIsDisplayed()
        compose.runOnIdle { scrollInfo = DialogScrollInfo(1f, 0.25f, false) }
        compose.onNodeWithTag("omnidroid-dialog-body-more-below").assertDoesNotExist()
        compose.onNodeWithTag("omnidroid-dialog-body-scrollbar").assertIsDisplayed()
        compose.runOnIdle { scrollInfo = null }
        compose.onNodeWithTag("omnidroid-dialog-body-more-below").assertDoesNotExist()
        compose.onNodeWithTag("omnidroid-dialog-body-scrollbar").assertDoesNotExist()
    }

}
