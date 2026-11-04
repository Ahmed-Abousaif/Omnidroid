package com.omnidroid.app.shared.library

import android.content.Context
import android.os.Build
import android.os.FileObserver
import com.omnidroid.lib.preferences.SharedPreferencesHelper
import com.omnidroid.lib.R as LibR
import timber.log.Timber
import java.io.File

/**
 * Foreground-only [FileObserver] for legacy file:// library roots.
 * Debounces bursts into a single library sync.
 */
class LocalFolderWatcher(
    private val context: Context,
    private val onDebouncedChange: () -> Unit,
) {
    private var observer: FileObserver? = null
    private var debounceRunnable: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun start() {
        stop()
        val root = resolveRoot() ?: return
        if (!root.isDirectory) return
        val mask =
            FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_FROM or
                FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE or FileObserver.ATTRIB or
                FileObserver.MODIFY
        observer =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                object : FileObserver(root, mask) {
                    override fun onEvent(
                        event: Int,
                        path: String?,
                    ) {
                        schedule()
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                object : FileObserver(root.absolutePath, mask) {
                    override fun onEvent(
                        event: Int,
                        path: String?,
                    ) {
                        schedule()
                    }
                }
            }.also { it.startWatching() }
        Timber.i("Watching local library folder ${root.absolutePath}")
    }

    fun stop() {
        observer?.stopWatching()
        observer = null
        debounceRunnable?.let { handler.removeCallbacks(it) }
        debounceRunnable = null
    }

    private fun schedule() {
        debounceRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable { onDebouncedChange() }
        debounceRunnable = runnable
        handler.postDelayed(runnable, DEBOUNCE_MS)
    }

    private fun resolveRoot(): File? {
        val prefString = context.getString(LibR.string.pref_key_legacy_external_folder)
        val preferenceManager = SharedPreferencesHelper.getLegacySharedPreferences(context)
        val path = preferenceManager.getString(prefString, null)
        return path?.let { File(it) }?.takeIf { it.exists() }
    }

    companion object {
        private const val DEBOUNCE_MS = 1_500L
    }
}
