package com.omnidroid.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import timber.log.Timber
import java.io.File

class AppUpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm =
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Timber.i("App update installed successfully")
                deletePendingApk(context)
                AppUpdateBus.notify(UpdateState.UpToDate)
            }
            else -> {
                Timber.w("App update install failed: status=$status message=$message")
                deletePendingApk(context)
                AppUpdateBus.notify(UpdateState.Error(message))
            }
        }
    }

    private fun deletePendingApk(context: Context) {
        val prefs = AppUpdatePreferences(context)
        val path = prefs.pendingApkPath
        prefs.pendingApkPath = null
        if (path != null) {
            runCatching { File(path).delete() }
        }
    }

    companion object {
        const val ACTION = "com.omnidroid.app.update.INSTALL_RESULT"
    }
}

/**
 * Tiny bridge so the install [BroadcastReceiver] can push state into the active [GitHubAppUpdater].
 */
object AppUpdateBus {
    @Volatile
    private var listener: ((UpdateState) -> Unit)? = null

    fun setListener(block: ((UpdateState) -> Unit)?) {
        listener = block
    }

    fun notify(state: UpdateState) {
        listener?.invoke(state)
    }
}
