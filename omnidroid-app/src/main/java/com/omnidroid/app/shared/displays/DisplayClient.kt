package com.omnidroid.app.shared.displays

import android.app.Activity
import android.app.ActivityOptions
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.provider.Settings
import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/** A lightweight process-local watcher; only extra displays need the main-process coordinator. */
class DisplayClient(private val application: Application) :
    Application.ActivityLifecycleCallbacks, DisplayManager.DisplayListener {
    private val handler = Handler(Looper.getMainLooper())
    private val displayManager by lazy { application.getSystemService(DisplayManager::class.java) }
    private val preferences by lazy { DisplayPreferences(application) }
    private val visible = linkedSetOf<Activity>()
    private val resumed = linkedSetOf<Activity>()
    private val layouts = mutableMapOf<Activity, View.OnLayoutChangeListener>()
    private var remote: Messenger? = null
    private var bound = false
    private var registered = false
    private var watching = false
    private var displayIds = emptySet<Int>()
    private val mutableState = MutableStateFlow(DisplaysState())
    val state: StateFlow<DisplaysState> = mutableState.asStateFlow()
    private val pending = mutableListOf<Pair<Int, Bundle>>()
    private val checkingActivities = mutableMapOf<String, Bundle>()
    private val endpoint by lazy {
        Messenger(
            Handler(Looper.getMainLooper()) { message ->
                if (!bound || remote == null) return@Handler true
                when (message.what) {
                    DisplayProtocol.STATE -> mutableState.value = DisplayProtocol.decode(message.data)
                    DisplayProtocol.LAUNCH_ACTIVITY -> launchOutput(message.data)
                    DisplayProtocol.REQUEST_HOST -> updateHost()
                }
                true
            },
        )
    }
    private val release =
        Runnable {
            if (visible.isEmpty()) {
                send(DisplayProtocol.REMOVE_HOST)
                unbind()
                if (watching) displayManager.unregisterDisplayListener(this)
                watching = false
                displayIds = emptySet()
                publishIdle()
            }
        }
    private val retryBind = Runnable { updateBinding() }
    private val connection by lazy {
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                binder: IBinder,
            ) {
                if (!bound || !needsCoordinator()) return
                remote = Messenger(binder)
                updateHost()
                pending.toList().also { pending.clear() }.forEach { (what, data) -> send(what, data) }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                if (!bound) return
                remote = null
                mutableState.value = mutableState.value.copy(connected = false, entries = emptyList())
            }

            override fun onBindingDied(name: ComponentName) {
                unbind()
                handler.postDelayed(retryBind, 500)
            }

            override fun onNullBinding(name: ComponentName) = unbind()
        }
    }

    fun register() {
        if (!registered) {
            registered = true
            application.registerActivityLifecycleCallbacks(this)
        }
    }

    fun refresh() {
        if (watching) refreshTopology()
        command(DisplayProtocol.REFRESH)
    }

    fun setAutomatic(enabled: Boolean) {
        preferences.automatic = enabled
        mutableState.value = mutableState.value.copy(automatic = enabled)
        command(
            DisplayProtocol.SET_AUTOMATIC,
            Bundle().apply {
                putBoolean(DisplayProtocol.ENABLED, enabled)
            },
        )
    }

    fun setHidden(
        id: Int,
        hidden: Boolean,
    ) = command(
        DisplayProtocol.SET_HIDDEN,
        Bundle().apply {
            putInt(DisplayProtocol.ID, id)
            putBoolean(DisplayProtocol.HIDDEN, hidden)
        },
    )

    private fun command(
        what: Int,
        data: Bundle = Bundle(),
    ) {
        if (!bound) return
        if (remote == null) pending.add(what to data) else send(what, data)
    }

    private fun refreshTopology() {
        try {
            // No metrics, activity-permission probes, or coordinator IPC on a single-screen device.
            displayIds = displayManager.displays.filter { it.isValid }.map { it.displayId }.toSet()
        } catch (error: RuntimeException) {
            Timber.w(error, "Unable to check connected displays")
            return
        }
        updateBinding()
    }

    private fun needsCoordinator() =
        DisplayPolicy.needsCoordinator(displayIds, visible.map { it.currentDisplayId() }.toSet())

    private fun updateBinding() {
        if (needsCoordinator()) {
            bind()
        } else {
            if (bound) {
                send(DisplayProtocol.REMOVE_HOST)
                unbind()
            } else {
                publishIdle()
            }
        }
        if (displayIds.size > 1) trackLayouts() else stopTrackingLayouts()
    }

    private fun publishIdle() {
        mutableState.value =
            DisplaysState(
                connected = watching && visible.isNotEmpty(),
                automatic = preferences.automatic,
            )
    }

    private fun trackLayouts() {
        visible.filter { it !in layouts }.forEach { activity ->
            var lastDisplay = activity.currentDisplayId()
            val listener =
                View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    val current = activity.currentDisplayId()
                    if (current != lastDisplay) {
                        lastDisplay = current
                        updateBinding()
                        updateHost()
                    }
                }
            layouts[activity] = listener
            activity.window.decorView.addOnLayoutChangeListener(listener)
        }
    }

    private fun stopTrackingLayouts() {
        layouts.forEach { (activity, listener) -> activity.window.decorView.removeOnLayoutChangeListener(listener) }
        layouts.clear()
    }

    private fun bind() {
        if (bound) return
        try {
            bound =
                application.bindService(
                    Intent(application, DisplaySessionService::class.java), connection, Context.BIND_AUTO_CREATE,
                )
        } catch (error: RuntimeException) {
            Timber.w(error, "Unable to connect display coordinator")
        }
    }

    private fun unbind() {
        if (bound) runCatching { application.unbindService(connection) }
        bound = false
        remote = null
        pending.clear()
        checkingActivities.clear()
        stopTrackingLayouts()
        publishIdle()
    }

    private fun updateHost() {
        if (remote == null) return
        send(
            DisplayProtocol.UPDATE_HOST,
            Bundle().apply {
                putIntArray(DisplayProtocol.HOSTS, visible.map { it.currentDisplayId() }.distinct().toIntArray())
                putBoolean(DisplayProtocol.FOREGROUND, resumed.isNotEmpty())
            },
        )
    }

    private fun send(
        what: Int,
        data: Bundle = Bundle(),
    ) {
        try {
            remote?.send(
                Message.obtain(null, what).also {
                    it.data = data
                    it.replyTo = endpoint
                },
            )
        } catch (error: RemoteException) {
            Timber.w(error, "Display coordinator disconnected")
            remote = null
        }
    }

    private fun launchOutput(data: Bundle) {
        val id = data.getInt(DisplayProtocol.ID, -1)
        val activity = resumed.lastOrNull { !it.isFinishing && !it.isDestroyed && it.currentDisplayId() != id }
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            send(DisplayProtocol.OUTPUT_FAILED, data)
            return
        }
        val sourceDisplay = activity.currentDisplayId()
        val token = data.getString(DisplayProtocol.TOKEN) ?: return
        checkingActivities[token] = data
        try {
            activity.startActivity(
                Intent(activity, SecondaryDisplayActivity::class.java).apply {
                    putExtras(data)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                },
                ActivityOptions.makeBasic().setLaunchDisplayId(id).toBundle(),
            )
            // OEMs can still pause or focus the wrong task. Fail closed instead of retrying forever.
            handler.postDelayed({
                checkingActivities.remove(token)
                if (resumed.none { it.currentDisplayId() == sourceDisplay && it.hasWindowFocus() }) {
                    send(DisplayProtocol.OUTPUT_FAILED, data)
                }
            }, 700)
        } catch (error: RuntimeException) {
            checkingActivities.remove(token)
            Timber.w(error, "Unable to open placeholder on display %d", id)
            send(DisplayProtocol.OUTPUT_FAILED, data)
        }
    }

    private fun primary(activity: Activity) = activity !is SecondaryDisplayActivity

    override fun onActivityStarted(activity: Activity) {
        if (!primary(activity)) return
        handler.removeCallbacks(release)
        visible.add(activity)
        if (!watching) {
            watching = true
            displayManager.registerDisplayListener(this, handler)
            refreshTopology()
        } else {
            updateBinding()
        }
        updateHost()
    }

    override fun onActivityResumed(activity: Activity) {
        if (!primary(activity)) return
        resumed.add(activity)
        updateHost()
    }

    override fun onActivityPaused(activity: Activity) {
        if (!primary(activity)) return
        resumed.remove(activity)
        if (resumed.isEmpty()) {
            // Some OEMs suspend the source as soon as a second activity opens. Record rejection
            // before closing the helper, or the source resuming could launch it in an endless loop.
            checkingActivities.values.toList().forEach { send(DisplayProtocol.OUTPUT_FAILED, it) }
            checkingActivities.clear()
        }
        updateHost()
    }

    override fun onActivityStopped(activity: Activity) {
        if (!primary(activity)) return
        visible.remove(activity)
        resumed.remove(activity)
        layouts.remove(activity)?.let { activity.window.decorView.removeOnLayoutChangeListener(it) }
        if (visible.isNotEmpty()) updateBinding()
        updateHost()
        if (visible.isEmpty()) handler.postDelayed(release, 600)
    }

    override fun onActivityDestroyed(activity: Activity) {
        visible.remove(activity)
        resumed.remove(activity)
        layouts.remove(activity)?.let { activity.window.decorView.removeOnLayoutChangeListener(it) }
    }

    override fun onDisplayAdded(displayId: Int) {
        if (watching) refreshTopology()
    }

    override fun onDisplayRemoved(displayId: Int) {
        if (watching) refreshTopology()
    }

    override fun onDisplayChanged(displayId: Int) = Unit // Detailed changes belong to the active coordinator.

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    companion object {
        @Suppress("DEPRECATION")
        internal fun Activity.currentDisplayId(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display?.displayId ?: 0
            } else {
                windowManager.defaultDisplay.displayId
            }

        fun openConnectionSettings(context: Context) {
            for (action in listOf(Settings.ACTION_CAST_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS)) {
                val intent = Intent(action)
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                    return
                } catch (_: RuntimeException) {
                    // Try the next system settings screen.
                }
            }
        }
    }
}
