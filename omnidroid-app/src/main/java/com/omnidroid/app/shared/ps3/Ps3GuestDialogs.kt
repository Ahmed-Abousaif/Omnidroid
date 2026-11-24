package com.omnidroid.app.shared.ps3

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.dialog.DialogActionRole
import com.omnidroid.app.mobile.shared.compose.dialog.DialogWidth
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogAction
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogButton
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogHandle
import com.omnidroid.app.mobile.shared.compose.dialog.showOmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import org.json.JSONObject
import timber.log.Timber

/** PS3 guest-system requests rendered by Omnidroid, with no emulator frontend. */
internal class Ps3GuestDialogs private constructor(
    private val activity: Activity,
    private val respond: (Long, Int, String) -> Boolean,
) {
    private class Request(
        val token: Long,
        val kind: Int,
        initial: JSONObject,
    ) {
        var json by mutableStateOf(initial.toString())
        val cancelResult get() =
            when (kind) {
                1 -> 3
                2 -> 1
                else -> -2
            }
        var handle: OmnidroidDialogHandle? = null
        var editor: EditText? = null
        var replied = false
    }

    private val requests = linkedMapOf<Long, Request>()

    fun detach() {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (current === this) current = null
        requests.values.toList().forEach { reply(it, it.cancelResult) }
    }

    private fun reply(
        request: Request,
        result: Int,
        text: String = "",
    ) {
        if (request.replied) return
        request.replied = true
        requests.remove(request.token)
        request.handle?.dismiss()
        runCatching { respond(request.token, result, text) }
            .onFailure { Timber.e(it, "PS3 dialog reply failed") }
    }

    private fun receive(
        token: Long,
        kind: Int,
        update: JSONObject,
    ) {
        when (kind) {
            4 ->
                requests[token]?.let { request ->
                    val json = JSONObject(request.json)
                    if (update.has("bar")) {
                        val key = "bar${update.getInt("bar")}"
                        val bar = json.optJSONObject(key) ?: JSONObject().put("maximum", 100).put("value", 0)
                        update.keys().forEach { field ->
                            if (field == "increment") {
                                bar.put("value", bar.optInt("value") + update.optInt(field))
                            } else if (field != "bar") {
                                bar.put(field, update.get(field))
                            }
                        }
                        json.put(key, bar)
                    } else {
                        update.keys().forEach { json.put(it, update.get(it)) }
                    }
                    request.json = json.toString()
                    if (update.has("initial")) request.editor?.setText(update.getString("initial"))
                }
            5 ->
                requests.remove(token)?.let {
                    it.replied = true
                    it.handle?.dismiss()
                }
            1, 2, 3 -> show(Request(token, kind, update))
        }
    }

    private fun show(request: Request) {
        val data = JSONObject(request.json)
        requests[request.token] = request
        val cancel = activity.getString(android.R.string.cancel)
        val actions = mutableListOf<OmnidroidDialogAction>()
        val cancelable = request.kind != 1 || data.optBoolean("cancelable", true)
        if (request.kind == 2) {
            val flags = data.optInt("prohibitFlags")
            request.editor =
                EditText(activity).apply {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    if (data.optBoolean("password")) {
                        inputType =
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                    setSingleLine(flags and 2 != 0)
                    imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                    filters =
                        arrayOf(
                            InputFilter.LengthFilter(data.optInt("maxLength", 511).coerceIn(0, 511)),
                            InputFilter { source, start, end, _, _, _ ->
                                val input = source.subSequence(start, end).toString()
                                val filtered =
                                    input.filterNot {
                                        flags and 1 != 0 &&
                                            it == ' ' ||
                                            flags and 2 != 0 &&
                                            it in "\r\n"
                                    }
                                filtered.takeIf { it != input }
                            },
                        )
                    setText(data.optString("initial"))
                    setSelection(text.length)
                    setOnEditorActionListener { _, action, _ ->
                        if (action == EditorInfo.IME_ACTION_DONE) {
                            reply(request, 0, text.toString())
                            true
                        } else {
                            false
                        }
                    }
                }
            actions += OmnidroidDialogAction(cancel, DialogActionRole.Cancel) { reply(request, 1) }
            actions +=
                OmnidroidDialogAction(activity.getString(R.string.game_keyboard_done)) {
                    reply(request, 0, request.editor!!.text.toString())
                }
        } else if (request.kind == 1) {
            when (data.optInt("buttons")) {
                1 -> {
                    actions +=
                        OmnidroidDialogAction(
                            activity.getString(R.string.ps3_guest_no),
                            DialogActionRole.Alternative,
                        ) { reply(request, 2) }
                    actions += OmnidroidDialogAction(activity.getString(R.string.ps3_guest_yes)) { reply(request, 1) }
                }
                2 -> actions += OmnidroidDialogAction(activity.getString(android.R.string.ok)) { reply(request, 1) }
            }
            if (cancelable) actions.add(0, OmnidroidDialogAction(cancel, DialogActionRole.Cancel) { reply(request, 3) })
        } else {
            actions += OmnidroidDialogAction(cancel, DialogActionRole.Cancel) { reply(request, -2) }
            if (data.optBoolean("allowNew")) {
                actions +=
                    OmnidroidDialogAction(activity.getString(R.string.ps3_guest_new_savedata)) { reply(request, -1) }
            }
        }
        val fallbackTitle =
            when (request.kind) {
                3 -> R.string.ps3_guest_savedata_title
                2 -> R.string.game_keyboard_title
                else -> R.string.ps3_guest_message_title
            }
        request.handle =
            showOmnidroidDialog(
                activity = activity,
                title = data.optString("title").takeIf { it.isNotBlank() } ?: activity.getString(fallbackTitle),
                actions = actions,
                cancelable = cancelable,
                forceDark = true,
                width = if (request.kind == 2) DialogWidth.Input else DialogWidth.Content,
                onCancel = { reply(request, request.cancelResult) },
                content = {
                    val json = JSONObject(request.json)
                    json.optString("message").takeIf { it.isNotBlank() }?.let { Text(it) }
                    if (request.kind == 2) {
                        val colors = chromeColors
                        AndroidView(modifier = Modifier.fillMaxWidth(), factory = {
                            request.editor!!.apply {
                                setTextColor(colors.content.toArgb())
                                setHintTextColor(colors.muted.toArgb())
                                requestFocus()
                                post {
                                    if (!request.replied) {
                                        (
                                            activity.getSystemService(
                                                Activity.INPUT_METHOD_SERVICE,
                                            ) as android.view.inputmethod.InputMethodManager
                                        ).showSoftInput(this, 0)
                                    }
                                }
                            }
                        })
                    }
                    repeat(json.optInt("progressBars").coerceIn(0, 2)) { index ->
                        val bar = json.optJSONObject("bar$index") ?: JSONObject()
                        bar.optString("message").takeIf { it.isNotBlank() }?.let { Text(it) }
                        val maximum = bar.optInt("maximum", 100).coerceAtLeast(1)
                        LinearProgressIndicator(progress = {
                            (bar.optInt("value").toFloat() / maximum).coerceIn(0f, 1f)
                        }, modifier = Modifier.fillMaxWidth())
                    }
                    if (request.kind == 3) {
                        val entries = json.optJSONArray("entries")
                        repeat(entries?.length() ?: 0) { index ->
                            val entry = entries!!.getJSONObject(index)
                            OmnidroidDialogButton(
                                onClick = { reply(request, index) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    listOf(
                                        entry.optString("title"),
                                        entry.optString("subtitle"),
                                        entry.optString("details"),
                                    ).filter {
                                        it.isNotBlank()
                                    }.joinToString("\n"),
                                )
                            }
                        }
                    }
                },
            )
        if (request.handle == null) reply(request, request.cancelResult)
    }

    companion object {
        private val main = Handler(Looper.getMainLooper())
        private var current: Ps3GuestDialogs? = null

        fun attach(
            activity: Activity,
            respond: (Long, Int, String) -> Boolean = Armsx3Native::dialogResult,
        ): Ps3GuestDialogs {
            current?.detach()
            return Ps3GuestDialogs(activity, respond).also { current = it }
        }

        fun deliver(
            token: Long,
            kind: Int,
            json: String,
        ) {
            main.post {
                runCatching {
                    val owner = current
                    if (owner != null) {
                        owner.receive(token, kind, JSONObject(json))
                    } else if (kind in
                        1..3
                    ) {
                        Armsx3Native.dialogResult(
                            token,
                            when (kind) {
                                1 -> 3
                                2 -> 1
                                else -> -2
                            },
                            "",
                        )
                    }
                }.onFailure {
                    Timber.e(it, "Invalid PS3 guest dialog request")
                    if (kind in 1..3) {
                        Armsx3Native.dialogResult(
                            token,
                            when (kind) {
                                1 -> 3
                                2 -> 1
                                else -> -2
                            },
                            "",
                        )
                    }
                }
            }
        }
    }
}
