package com.omnidroid.app.testing

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.omnidroid.BuildConfig
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.ui.AppTheme
import com.omnidroid.app.update.AppUpdateBus
import com.omnidroid.app.update.AppUpdateOverlay
import com.omnidroid.app.update.GitHubAppUpdater
import com.omnidroid.app.update.UpdateState
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AppReinstallTest {
    @get:Rule
    val compose = createAndroidComposeRule<LibraryStressActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = SharedPreferencesHelper.getSharedPreferences(context)
    private var savedPreferences: Map<String, *> = emptyMap<String, String>()
    private val apkRequests = AtomicInteger()
    private var releaseJson =
        """
        {
          "tag_name": "v${BuildConfig.VERSION_NAME}",
          "assets": [{"name": "latest.apk", "browser_download_url": "https://example.com/latest.apk"}]
        }
        """.trimIndent()

    @Before
    fun prepare() {
        savedPreferences = preferences.all.filterKeys { it.startsWith("app_update_") }
        clearUpdatePreferences()
    }

    @After
    fun restore() {
        AppUpdateBus.setListener(null)
        clearUpdatePreferences()
        preferences
            .edit()
            .apply {
                savedPreferences.forEach { (key, value) -> putString(key, value as String?) }
            }.commit()
    }

    private fun clearUpdatePreferences() {
        preferences
            .edit()
            .apply {
                preferences.all.keys
                    .filter { it.startsWith("app_update_") }
                    .forEach(::remove)
            }.commit()
    }

    private fun updater(): GitHubAppUpdater {
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val isApk =
                        chain
                            .request()
                            .url.encodedPath
                            .endsWith(".apk")
                    if (isApk) apkRequests.incrementAndGet()
                    // Stop at the download boundary so the test never installs over itself.
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(if (isApk) 503 else 200)
                        .message("Fixture response")
                        .body((if (isApk) "Unavailable" else releaseJson).toResponseBody())
                        .build()
                }.build()
        return GitHubAppUpdater(context, client)
    }

    private fun showOverlay(updater: GitHubAppUpdater) {
        lateinit var launcher: ActivityResultLauncher<IntentSenderRequest>
        compose.activityRule.scenario.onActivity { activity ->
            launcher =
                activity.activityResultRegistry.register(
                    "reinstall-test",
                    ActivityResultContracts.StartIntentSenderForResult(),
                ) {}
        }
        compose.setContent {
            val state by updater.state.collectAsState()
            AppTheme {
                AppUpdateOverlay(
                    state = state,
                    supportsGitHubOptions = true,
                    onUpdate = { updater.startUpdate(compose.activity, launcher) },
                    onLater = updater::dismissAvailable,
                    onSkip = updater::skipCurrentVersion,
                    onRestart = {},
                    onDismissError = updater::dismissAvailable,
                )
            }
        }
    }

    @Test
    fun manualSameVersionCheckOffersReinstallAndDownloadsLatestApk() {
        val updater = updater()
        runBlocking { updater.checkForUpdate(force = true) }
        assertEquals(UpdateState.UpToDate(canReinstall = true), updater.state.value)
        showOverlay(updater)

        compose.onNodeWithText(context.getString(R.string.app_update_up_to_date_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.app_update_action_reinstall)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { updater.state.value is UpdateState.Error }
        compose.onNodeWithText("Download HTTP 503").assertIsDisplayed()
        assertEquals(1, apkRequests.get())
    }

    @Test
    fun manualCheckWithoutApkDoesNotOfferReinstall() {
        releaseJson = """{"tag_name": "v${BuildConfig.VERSION_NAME}", "assets": []}"""
        val updater = updater()
        runBlocking { updater.checkForUpdate(force = true) }
        assertEquals(UpdateState.UpToDate(), updater.state.value)
        showOverlay(updater)

        compose.onNodeWithText(context.getString(R.string.app_update_action_reinstall)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.settings_action_close)).performClick()
        compose.runOnIdle { assertEquals(UpdateState.Idle, updater.state.value) }
    }

    @Test
    fun automaticSameVersionCheckStaysSilent() {
        val updater = updater()
        runBlocking { updater.checkForUpdate(force = false) }
        assertEquals(UpdateState.Idle, updater.state.value)
        assertEquals(0, apkRequests.get())
    }
}
