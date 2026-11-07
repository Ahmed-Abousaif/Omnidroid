package com.omnidroid.app.update

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.isFlexibleUpdateAllowed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.android.gms.tasks.Task

class PlayAppUpdater(
    context: Context,
) : AppUpdater {
    private val appContext = context.applicationContext
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(appContext)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    private var listenerRegistered = false

    private val installListener =
        InstallStateUpdatedListener { state ->
            when (state.installStatus()) {
                InstallStatus.DOWNLOADING -> {
                    val total = state.totalBytesToDownload().coerceAtLeast(1L)
                    val progress = state.bytesDownloaded().toFloat() / total.toFloat()
                    _state.value = UpdateState.Downloading(progress.coerceIn(0f, 1f))
                }
                InstallStatus.DOWNLOADED -> {
                    _state.value = UpdateState.ReadyToInstall
                    unregisterListener()
                }
                InstallStatus.FAILED, InstallStatus.CANCELED -> {
                    _state.value = UpdateState.Idle
                    unregisterListener()
                }
                InstallStatus.INSTALLED -> {
                    _state.value = UpdateState.UpToDate
                    unregisterListener()
                }
                else -> Unit
            }
        }

    override val state: StateFlow<UpdateState> = _state.asStateFlow()
    override val supportsGitHubOptions: Boolean = false

    override suspend fun checkForUpdate(force: Boolean) {
        _state.value = UpdateState.Checking
        runCatching {
            val info = appUpdateManager.appUpdateInfo.awaitTask()
            when {
                info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isFlexibleUpdateAllowed -> {
                    val versionLabel = info.availableVersionCode().toString()
                    _state.value = UpdateState.Available(version = versionLabel, notes = "")
                }
                info.installStatus() == InstallStatus.DOWNLOADED -> {
                    _state.value = UpdateState.ReadyToInstall
                }
                else -> {
                    _state.value = if (force) UpdateState.UpToDate else UpdateState.Idle
                }
            }
        }.onFailure { error ->
            Timber.d(error, "Play in-app update check skipped")
            _state.value = UpdateState.Idle
        }
    }

    private suspend fun <T> Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { result -> cont.resume(result) }
            addOnFailureListener { error -> cont.resumeWithException(error) }
        }

    override fun startUpdate(
        activity: Activity,
        updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
    ) {
        registerListener()
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE ||
                    !info.isFlexibleUpdateAllowed
                ) {
                    _state.value = UpdateState.Idle
                    return@addOnSuccessListener
                }
                runCatching {
                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        updateLauncher,
                        AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                    )
                }.onFailure { error ->
                    Timber.d(error, "Failed to start Play update flow")
                    _state.value = UpdateState.Idle
                    unregisterListener()
                }
            }
            .addOnFailureListener { error ->
                Timber.d(error, "Play update info unavailable")
                _state.value = UpdateState.Idle
                unregisterListener()
            }
    }

    override fun completeFlexibleUpdate() {
        appUpdateManager.completeUpdate()
            .addOnFailureListener { error ->
                Timber.d(error, "completeUpdate failed")
                _state.value = UpdateState.Idle
            }
    }

    override fun dismissAvailable() {
        if (_state.value is UpdateState.Available || _state.value is UpdateState.UpToDate) {
            _state.value = UpdateState.Idle
        }
    }

    override fun skipCurrentVersion() = Unit

    override fun onHostResumed(activity: Activity) {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                when {
                    info.installStatus() == InstallStatus.DOWNLOADED -> {
                        _state.value = UpdateState.ReadyToInstall
                    }
                    info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                        registerListener()
                        _state.value = UpdateState.Downloading(0f)
                    }
                }
            }
            .addOnFailureListener {
                // Quiet: sideloaded or Play Core unavailable.
            }
    }

    override fun onUnknownSourcesReturned(activity: Activity) = Unit

    private fun registerListener() {
        if (listenerRegistered) return
        appUpdateManager.registerListener(installListener)
        listenerRegistered = true
    }

    private fun unregisterListener() {
        if (!listenerRegistered) return
        runCatching { appUpdateManager.unregisterListener(installListener) }
        listenerRegistered = false
    }
}
