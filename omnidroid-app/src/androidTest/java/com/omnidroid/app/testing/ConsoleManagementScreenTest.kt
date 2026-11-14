package com.omnidroid.app.testing

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import com.omnidroid.R
import com.omnidroid.app.mobile.feature.addconsole.AddConsoleViewModel
import com.omnidroid.app.mobile.feature.addconsole.AddConsoleViewModel.BusyAction
import com.omnidroid.app.mobile.feature.addconsole.AddConsoleViewModel.ConsoleItem
import com.omnidroid.app.mobile.feature.addconsole.ConsoleManagementContent
import com.omnidroid.app.mobile.feature.library.LibrarySyncEmpty
import com.omnidroid.app.mobile.shared.compose.ui.AppTheme
import com.omnidroid.app.mobile.shared.controller.ControllerNavigationState
import com.omnidroid.app.mobile.shared.controller.LocalControllerNavigation
import com.omnidroid.lib.library.MetaSystemID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Run on portrait, square and landscape emulator profiles. Callbacks never touch real games. */
class ConsoleManagementScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<LibraryStressActivity>()

    private val nes =
        ConsoleItem(
            metaSystemID = MetaSystemID.NES,
            registeredOrHasGames = true,
            coreInstalled = false,
            coreBundled = false,
            coreDisplayName = "FCEUmm",
            databaseInstalled = false,
            databaseGameCount = 1200,
            libraryGameCount = 42,
            installedDatabaseCount = 1,
            expectedDatabaseCount = 2,
            manuallyUninstalled = true,
        )
    private val snes =
        nes.copy(
            metaSystemID = MetaSystemID.SNES,
            registeredOrHasGames = false,
            coreDisplayName = "Snes9x",
            coreInstalled = true,
            coreBundled = true,
            databaseInstalled = true,
            installedDatabaseCount = 1,
            expectedDatabaseCount = 1,
            libraryGameCount = 0,
        )

    private fun label(id: Int) = compose.activity.getString(id)

    private fun screenshot(name: String) {
        val profile = InstrumentationRegistry.getArguments().getString("displayProfile") ?: "default"
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File(compose.activity.getExternalFilesDir(null), "consoles-$profile-$name.png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun catalogueAndEveryManagementActionRemainReachable() {
        var repaired: MetaSystemID? = null
        var downloaded: MetaSystemID? = null
        var deleted: MetaSystemID? = null
        var removed: MetaSystemID? = null
        compose.setContent {
            AppTheme {
                ConsoleManagementContent(
                    state = AddConsoleViewModel.UiState(consoles = listOf(nes, snes), loading = false),
                    onInstall = { repaired = it },
                    onRefetchDatabase = { downloaded = it },
                    onDeleteDatabase = { deleted = it },
                    onRemoveConsole = { removed = it },
                    onConsumeError = {},
                )
            }
        }
        compose.onNodeWithText(label(R.string.title_add_console)).assertDoesNotExist()
        compose.onNodeWithText("1/27 systems").assertIsDisplayed()
        screenshot("catalogue")
        compose.onNodeWithTag("console-catalogue").performScrollToIndex(1)
        compose.onNodeWithTag("console-NES").performClick()
        compose.onNodeWithTag("console-detail").assertIsDisplayed()
        screenshot("detail")
        compose.onNodeWithText(
            label(R.string.consoles_install_console),
        ).performScrollTo().assertIsDisplayed().performClick()
        assertEquals(MetaSystemID.NES, repaired)
        compose.onNodeWithContentDescription(
            label(R.string.consoles_download_database_full),
        ).performScrollTo().assertIsDisplayed().performClick()
        assertEquals(MetaSystemID.NES, downloaded)
        compose.onNodeWithContentDescription(
            label(R.string.consoles_delete_database_full),
        ).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.consoles_delete_database_title)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.consoles_confirm_delete)).performClick()
        assertEquals(MetaSystemID.NES, deleted)
        compose.onNodeWithText(
            label(R.string.consoles_remove_console_full),
        ).performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText(label(R.string.consoles_confirm_remove)).performClick()
        assertEquals(MetaSystemID.NES, removed)
    }

    @Test
    fun otherConsoleOperationDisablesAllManagementActions() {
        val state = mutableStateOf(AddConsoleViewModel.UiState(consoles = listOf(nes, snes), loading = false))
        compose.setContent {
            AppTheme {
                ConsoleManagementContent(state.value, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("console-catalogue").performScrollToIndex(1)
        compose.onNodeWithTag("console-NES").performClick()
        compose.runOnIdle {
            state.value =
                state.value.copy(
                    busyMetaSystemID = MetaSystemID.SNES,
                    busyAction = BusyAction.REFETCH_DATABASE,
                )
        }
        compose.onNodeWithText(label(R.string.consoles_install_console)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription(
            label(R.string.consoles_download_database_full),
        ).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription(
            label(R.string.consoles_delete_database_full),
        ).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(label(R.string.consoles_remove_console_full)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun searchEmptyStateCanBeReset() {
        compose.setContent {
            AppTheme {
                ConsoleManagementContent(AddConsoleViewModel.UiState(consoles = listOf(nes, snes), loading = false), {
                }, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText(label(R.string.title_search)).performTextInput("no-such-system")
        compose.onNodeWithText("1/27 systems").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { activity ->
            (
                activity.getSystemService(
                    android.content.Context.INPUT_METHOD_SERVICE,
                ) as android.view.inputmethod.InputMethodManager
            )
                .hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        compose.onNodeWithText(label(R.string.consoles_reset_search)).performScrollTo().performClick()
        compose.onNodeWithTag("console-catalogue").performScrollToIndex(1)
        compose.onNodeWithTag("console-NES").assertIsDisplayed()
    }

    @Test
    fun databaseDeletionKeepsTheOpenConsoleSelected() {
        val state = mutableStateOf(AddConsoleViewModel.UiState(consoles = listOf(nes, snes), loading = false))
        var downloaded: MetaSystemID? = null
        compose.setContent {
            AppTheme { ConsoleManagementContent(state.value, {}, { downloaded = it }, {}, {}, {}) }
        }
        compose.onNodeWithTag("console-catalogue").performScrollToIndex(1)
        compose.onNodeWithTag("console-NES").performClick()
        compose.runOnIdle {
            state.value = state.value.copy(consoles = listOf(nes.copy(installedDatabaseCount = 0), snes))
        }
        compose.onNodeWithContentDescription(
            label(R.string.consoles_download_database_full),
        ).performScrollTo().performClick()
        assertEquals(MetaSystemID.NES, downloaded)
    }

    @Test
    fun shouldersAndConfirmOpenTheFocusedConsole() {
        val navigation = ControllerNavigationState()
        var added: MetaSystemID? = null
        compose.setContent {
            AppTheme {
                CompositionLocalProvider(LocalControllerNavigation provides navigation) {
                    ConsoleManagementContent(
                        AddConsoleViewModel.UiState(consoles = listOf(nes, snes), loading = false),
                        { added = it },
                        {},
                        {},
                        {},
                        {},
                    )
                }
            }
        }
        compose.runOnIdle { navigation.onCarouselNext() }
        compose.waitForIdle()
        compose.onNodeWithTag("console-SNES").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("console-detail").assertIsDisplayed()
        compose.onNodeWithText(label(R.string.consoles_install_console)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.consoles_add_library)).assertDoesNotExist()
    }

    @Test
    fun firstTimeConsoleExplainsLibrarySetupWithoutInstallOrAddActions() {
        val fresh = nes.copy(
            registeredOrHasGames = false, coreInstalled = false, manuallyUninstalled = false,
            installedDatabaseCount = 0, libraryGameCount = 0,
        )
        compose.setContent {
            AppTheme {
                ConsoleManagementContent(
                    AddConsoleViewModel.UiState(consoles = listOf(fresh), loading = false),
                    {}, {}, {}, {}, {}, initialConsole = MetaSystemID.NES,
                )
            }
        }
        compose.onNodeWithText(label(R.string.consoles_emulator_auto), substring = true).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.consoles_install_console)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.consoles_add_library)).assertDoesNotExist()
        screenshot("first-time")
    }

    @Test
    fun missingAssetsHaveAnAccessibleReinstallActionAndRespectBusyState() {
        val console = nes.copy(
            coreInstalled = true, manuallyUninstalled = false, hasAssets = true, assetsInstalled = false,
        )
        val state = mutableStateOf(AddConsoleViewModel.UiState(consoles = listOf(console), loading = false))
        var retries = 0
        compose.setContent {
            AppTheme {
                ConsoleManagementContent(
                    state.value, {}, {}, {}, {}, {},
                    onRefreshAssets = { retries++ }, initialConsole = MetaSystemID.NES,
                )
            }
        }
        compose.onNodeWithText(label(R.string.consoles_assets_missing)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(label(R.string.consoles_assets_refresh)).performScrollTo().performClick()
        assertEquals(1, retries)
        compose.runOnIdle {
            state.value = state.value.copy(busyMetaSystemID = MetaSystemID.NES, busyAction = BusyAction.REFRESH_ASSETS)
        }
        compose.onNodeWithText(label(R.string.consoles_assets_refresh)).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(busyMetaSystemID = null, busyAction = null) }
        compose.onNodeWithText(label(R.string.consoles_assets_refresh)).performScrollTo().performClick()
        assertEquals(2, retries)
        screenshot("assets-recovery")
    }

    @Test
    fun recoveryKeepsTheRequestedConsoleSelectedWhileTheCatalogueLoads() {
        val state = mutableStateOf(AddConsoleViewModel.UiState())
        val requested = nes.copy(metaSystemID = MetaSystemID.WII, coreDisplayName = "Dolphin")
        compose.setContent {
            AppTheme {
                ConsoleManagementContent(
                    state.value, {}, {}, {}, {}, {}, initialConsole = MetaSystemID.WII,
                )
            }
        }
        compose.runOnIdle { state.value = state.value.copy(consoles = listOf(nes, requested), loading = false) }
        compose.onNodeWithTag("console-detail").assertIsDisplayed()
        compose.onNodeWithText("Dolphin", substring = true).assertIsDisplayed()
    }

    @Test
    fun uninstallRequiresConfirmationAndOnlyThatConsoleGetsInstall() {
        val installed = nes.copy(coreInstalled = true, manuallyUninstalled = false, canUninstallCore = true)
        val fresh = snes.copy(coreInstalled = false, coreBundled = false, manuallyUninstalled = false)
        val state = mutableStateOf(AddConsoleViewModel.UiState(consoles = listOf(installed, fresh), loading = false))
        val selection = mutableStateOf(MetaSystemID.NES)
        var uninstalled: MetaSystemID? = null
        compose.setContent {
            AppTheme {
                ConsoleManagementContent(
                    state.value, {}, {}, {}, {}, {},
                    initialConsole = selection.value,
                    onUninstallCore = { id ->
                        uninstalled = id
                        state.value = state.value.copy(
                            consoles = listOf(installed.copy(coreInstalled = false, manuallyUninstalled = true, canUninstallCore = false), fresh),
                        )
                    },
                )
            }
        }
        compose.onNodeWithContentDescription(label(R.string.consoles_uninstall_core)).performScrollTo().performClick()
        assertEquals(null, uninstalled)
        compose.onNodeWithText(label(R.string.consoles_uninstall_core_title)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.consoles_uninstall_core)).performClick()
        assertEquals(MetaSystemID.NES, uninstalled)
        compose.onNodeWithText(label(R.string.consoles_install_console)).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { selection.value = MetaSystemID.SNES }
        compose.onNodeWithText(label(R.string.consoles_install_console)).assertDoesNotExist()
    }

    @Test
    fun initialLibraryActionSaysPickAGamesFolderAndDisablesWhileScanning() {
        val scanning = mutableStateOf(false)
        var picks = 0
        compose.setContent {
            AppTheme { LibrarySyncEmpty(Modifier, scanning.value) { picks++ } }
        }
        compose.onNodeWithText(label(R.string.rescan)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.library_pick_games_folder)).assertIsDisplayed().performClick()
        assertEquals(1, picks)
        compose.runOnIdle { scanning.value = true }
        compose.onNodeWithText(label(R.string.library_scanning)).assertIsNotEnabled()
    }

    @Test
    fun directLaunchRecoveryCanReinstallAssetsWithoutAddingGamesFromConsoles() {
        val external = nes.copy(
            metaSystemID = MetaSystemID.PSP, registeredOrHasGames = false, libraryGameCount = 0,
            coreInstalled = true, coreBundled = true, manuallyUninstalled = false,
            hasDownloadedCore = false, hasAssets = true, assetsInstalled = false,
        )
        var repaired: MetaSystemID? = null
        compose.setContent {
            AppTheme {
                ConsoleManagementContent(
                    AddConsoleViewModel.UiState(consoles = listOf(external), loading = false),
                    {}, {}, {}, {}, {}, initialConsole = MetaSystemID.PSP,
                    onRefreshAssets = { repaired = it },
                )
            }
        }
        compose.onNodeWithText(label(R.string.consoles_assets_refresh)).performScrollTo().performClick()
        assertEquals(MetaSystemID.PSP, repaired)
        compose.onNodeWithText(label(R.string.consoles_install_console)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.consoles_add_library)).assertDoesNotExist()
    }
}
