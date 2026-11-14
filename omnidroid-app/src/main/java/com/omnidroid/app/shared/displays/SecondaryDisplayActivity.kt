package com.omnidroid.app.shared.displays

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import com.omnidroid.app.shared.displays.DisplayClient.Companion.currentDisplayId

/** Fallback only for displays on which Android allows independent activities. */
class SecondaryDisplayActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var remote: Messenger? = null
    private var bound = false
    private var validated = false
    private val data get() =
        Bundle().apply {
            putInt(DisplayProtocol.ID, intent.getIntExtra(DisplayProtocol.ID, -1))
            putString(DisplayProtocol.TOKEN, intent.getStringExtra(DisplayProtocol.TOKEN))
        }
    private val endpoint =
        Messenger(
            Handler(Looper.getMainLooper()) { message ->
                if (message.what == DisplayProtocol.CLOSE_OUTPUT) finishAndRemoveTask()
                true
            },
        )
    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                binder: IBinder,
            ) {
                remote = Messenger(binder)
                // Wait for layout so the coordinator never reports a merely launched Activity as visible.
                window.decorView.post { reportReady() }
            }

            override fun onServiceDisconnected(name: ComponentName) = finishAndRemoveTask()

            override fun onBindingDied(name: ComponentName) = finishAndRemoveTask()

            override fun onNullBinding(name: ComponentName) = finishAndRemoveTask()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.configurePlaceholderWindow()
        if (intent.getStringExtra(DisplayProtocol.TOKEN) == null ||
            intent.getIntExtra(DisplayProtocol.ID, -1) != currentDisplayId()
        ) {
            finishAndRemoveTask()
            return
        }
        setContentView(SecondaryDisplayContent(this))
        try {
            bound = bindService(Intent(this, DisplaySessionService::class.java), connection, Context.BIND_AUTO_CREATE)
        } catch (_: RuntimeException) {
            finishAndRemoveTask()
        }
        if (!bound) finishAndRemoveTask()
        handler.postDelayed({ if (!validated) finishAndRemoveTask() }, 3500)
    }

    private fun reportReady() {
        if (isFinishing || isDestroyed) return
        validated = true
        send(DisplayProtocol.OUTPUT_READY)
    }

    private fun send(what: Int) {
        try {
            remote?.send(
                Message.obtain(null, what).also {
                    it.data = data
                    it.replyTo = endpoint
                },
            )
        } catch (_: RemoteException) {
            finishAndRemoveTask()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            send(DisplayProtocol.OUTPUT_FAILED)
            finishAndRemoveTask()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (currentDisplayId() != intent.getIntExtra(DisplayProtocol.ID, -1)) {
            send(DisplayProtocol.OUTPUT_FAILED)
            finishAndRemoveTask()
        } else {
            setContentView(SecondaryDisplayContent(this))
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        send(DisplayProtocol.OUTPUT_GONE)
        if (bound) runCatching { unbindService(connection) }
        remote = null
        super.onDestroy()
    }
}
