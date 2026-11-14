package com.omnidroid.app.shared.displays

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import timber.log.Timber
import java.util.UUID

/** One main-process owner for all windows, even when gameplay lives in :game. */
class DisplaySessionService : Service() {
    private val handler =
        Handler(Looper.getMainLooper()) { message ->
            receive(message)
            true
        }
    private val endpoint = Messenger(handler)
    private lateinit var repository: DisplayRepository
    private val preferences by lazy { DisplayPreferences(this) }
    private var automatic = true
    private var facts = emptyList<DisplayFacts>()
    private val hosts = mutableMapOf<IBinder, Host>()
    private val hidden = mutableSetOf<Int>() // Connection-scoped: never persist Android display IDs.
    private val failures = mutableSetOf<Int>()
    private val presentations = mutableMapOf<Int, PlaceholderPresentation>()
    private val activities = mutableMapOf<Int, ActivityOutput>()
    private var sequence = 0L
    private val settle = Runnable { reconcile() }

    private data class Host(
        val endpoint: Messenger,
        val death: IBinder.DeathRecipient,
        var displays: Set<Int>,
        var foreground: Boolean,
        var sequence: Long,
    )

    private data class ActivityOutput(
        val token: String,
        var endpoint: Messenger? = null,
        var death: IBinder.DeathRecipient? = null,
    )

    override fun onCreate() {
        super.onCreate()
        automatic = preferences.automatic
        repository =
            DisplayRepository(this) { updated ->
                val previous = facts.associateBy { it.id }
                facts = updated
                val connectedIds = updated.map { it.id }.toSet()
                hidden.retainAll(connectedIds)
                failures.retainAll(connectedIds)
                updated.filter { previous[it.id] != it }.forEach {
                    failures.remove(it.id)
                    close(it.id)
                }
                hosts.values.toList().forEach { send(it.endpoint, DisplayProtocol.REQUEST_HOST) }
                reconcile()
            }
        repository.start()
    }

    override fun onBind(intent: Intent): IBinder = endpoint.binder

    private fun receive(message: Message) {
        if (message.sendingUid != applicationInfo.uid) return
        val data = message.data
        when (message.what) {
            DisplayProtocol.UPDATE_HOST -> {
                val reply = message.replyTo ?: return
                val key = reply.binder
                val host =
                    hosts[key] ?: run {
                        val death =
                            IBinder.DeathRecipient {
                                handler.post { removeHost(key) }
                            }
                        try {
                            key.linkToDeath(death, 0)
                        } catch (_: RemoteException) {
                            return
                        }
                        Host(reply, death, emptySet(), false, 0).also { hosts[key] = it }
                    }
                host.displays = data.getIntArray(DisplayProtocol.HOSTS)?.toSet().orEmpty()
                host.foreground = data.getBoolean(DisplayProtocol.FOREGROUND)
                if (host.foreground) host.sequence = ++sequence
                scheduleReconcile()
            }
            DisplayProtocol.REMOVE_HOST -> message.replyTo?.binder?.let(::removeHost)
            DisplayProtocol.SET_AUTOMATIC -> {
                automatic = data.getBoolean(DisplayProtocol.ENABLED)
                preferences.automatic = automatic
                if (automatic) failures.clear()
                reconcile()
            }
            DisplayProtocol.SET_HIDDEN -> {
                val id = data.getInt(DisplayProtocol.ID, -1)
                if (facts.none { it.id == id }) return
                if (data.getBoolean(DisplayProtocol.HIDDEN)) hidden.add(id) else hidden.remove(id)
                failures.remove(id)
                reconcile()
            }
            DisplayProtocol.REFRESH -> repository.refresh()
            DisplayProtocol.OUTPUT_READY -> {
                val id = data.getInt(DisplayProtocol.ID, -1)
                val output = activities[id]
                val reply = message.replyTo ?: return
                if (output == null || output.token != data.getString(DisplayProtocol.TOKEN)) {
                    send(reply, DisplayProtocol.CLOSE_OUTPUT)
                    return
                }
                if (output.endpoint == null) {
                    val death =
                        IBinder.DeathRecipient {
                            handler.post { failActivity(id, output.token) }
                        }
                    try {
                        reply.binder.linkToDeath(death, 0)
                    } catch (_: RemoteException) {
                        failActivity(id, output.token)
                        return
                    }
                    output.death = death
                    output.endpoint = reply
                }
                publish()
            }
            DisplayProtocol.OUTPUT_FAILED, DisplayProtocol.OUTPUT_GONE -> {
                failActivity(data.getInt(DisplayProtocol.ID, -1), data.getString(DisplayProtocol.TOKEN))
            }
        }
    }

    private fun removeHost(key: IBinder) {
        hosts.remove(key)?.let { runCatching { key.unlinkToDeath(it.death, 0) } }
        scheduleReconcile()
    }

    private fun scheduleReconcile() {
        handler.removeCallbacks(settle)
        // Let a new Activity/process take ownership without tearing down every placeholder.
        if (hosts.values.any { it.foreground }) reconcile() else handler.postDelayed(settle, 300)
    }

    private fun entries(): List<DisplayEntry> {
        val displays = hosts.values.flatMap { it.displays }.toSet()
        return facts.mapNotNull { fact ->
            DisplayPolicy.evaluate(
                fact,
                displays,
                hosts.values.any { it.foreground },
                automatic,
                fact.id in hidden,
                presentations[fact.id]?.isShowing == true || activities[fact.id]?.endpoint != null,
                fact.id in failures,
            )?.let { entry ->
                if (activities.containsKey(fact.id)) entry.copy(output = DisplayOutput.ACTIVITY) else entry
            }
        }
    }

    private fun reconcile() {
        val entries = entries()
        val eligible = entries.filter { it.status == DisplayStatus.READY || it.status == DisplayStatus.SHOWING }
        val ids = eligible.map { it.facts.id }.toSet()
        (presentations.keys + activities.keys).filter { it !in ids }.forEach(::close)
        eligible.filter { it.status == DisplayStatus.READY }.forEach { entry ->
            val id = entry.facts.id
            if (id !in presentations && id !in activities) {
                if (entry.output == DisplayOutput.PRESENTATION && showPresentation(id)) return@forEach
                if (entry.facts.activityAllowed) launchActivity(id) else failures.add(id)
            }
        }
        publish()
    }

    private fun showPresentation(id: Int): Boolean {
        val display = repository.display(id) ?: return false
        return try {
            val presentation = PlaceholderPresentation(this, display)
            presentation.setOnDismissListener {
                if (presentations[id] === presentation) {
                    presentations.remove(id)
                    failures.add(id) // A policy dismissal must not cause a retry loop.
                    publish()
                }
            }
            presentation.show()
            presentations[id] = presentation
            true
        } catch (error: RuntimeException) {
            Timber.w(error, "Presentation unavailable on display %d", id)
            false
        }
    }

    private fun launchActivity(id: Int) {
        val host = hosts.values.filter { it.foreground }.maxByOrNull { it.sequence } ?: return
        val token = UUID.randomUUID().toString()
        activities[id] = ActivityOutput(token)
        if (!send(
                host.endpoint,
                DisplayProtocol.LAUNCH_ACTIVITY,
                Bundle().apply {
                    putInt(DisplayProtocol.ID, id)
                    putString(DisplayProtocol.TOKEN, token)
                },
            )
        ) {
            failActivity(id, token)
        }
        handler.postDelayed({
            if (activities[id]?.token == token && activities[id]?.endpoint == null) failActivity(id, token)
        }, 3000)
    }

    private fun failActivity(
        id: Int,
        token: String?,
    ) {
        if (token == null || activities[id]?.token != token) return
        failures.add(id)
        close(id)
        publish()
    }

    private fun close(id: Int) {
        presentations.remove(id)?.let { runCatching { it.dismiss() } }
        activities.remove(id)?.let { output ->
            output.endpoint?.let {
                send(it, DisplayProtocol.CLOSE_OUTPUT)
                output.death?.let { death -> runCatching { it.binder.unlinkToDeath(death, 0) } }
            }
        }
    }

    private fun publish() {
        val data = DisplayProtocol.encode(DisplaysState(true, automatic, entries()))
        hosts.values.toList().forEach { send(it.endpoint, DisplayProtocol.STATE, data) }
    }

    private fun send(
        endpoint: Messenger,
        what: Int,
        data: Bundle = Bundle(),
    ): Boolean =
        try {
            endpoint.send(Message.obtain(null, what).also { it.data = data })
            true
        } catch (_: RemoteException) {
            false
        }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        repository.stop()
        (presentations.keys + activities.keys).toSet().forEach(::close)
        hosts.forEach { (key, host) -> runCatching { key.unlinkToDeath(host.death, 0) } }
        hosts.clear()
        super.onDestroy()
    }
}
