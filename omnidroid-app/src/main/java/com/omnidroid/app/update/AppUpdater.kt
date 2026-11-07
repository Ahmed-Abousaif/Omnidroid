package com.omnidroid.app.update

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.StateFlow

/**
 * Flavor-specific app update checker. Implementations live in `src/free` (GitHub Releases)
 * and `src/play` (Play In-App Updates). Obtained via [UpdaterProvider].
 */
interface AppUpdater {
    val state: StateFlow<UpdateState>

    /** True for the GitHub/free distribution (skip-version UI). */
    val supportsGitHubOptions: Boolean

    /**
     * @param force when true, surfaces "up to date" / error UI for manual checks.
     */
    suspend fun checkForUpdate(force: Boolean = false)

    fun startUpdate(
        activity: Activity,
        updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
    )

    /** Play flexible: call [com.google.android.play.core.appupdate.AppUpdateManager.completeUpdate]. */
    fun completeFlexibleUpdate()

    fun dismissAvailable()

    fun skipCurrentVersion()

    /** Resume Play download / pending GitHub install when returning to foreground. */
    fun onHostResumed(activity: Activity)

    /** After returning from [Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES]. */
    fun onUnknownSourcesReturned(activity: Activity)
}
