package com.omnidroid.app.shared.game.view

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.omnidroid.R
import com.omnidroid.app.mobile.shared.compose.dialog.DialogActionRole
import com.omnidroid.app.mobile.shared.compose.dialog.DialogWidth
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogAction
import com.omnidroid.app.mobile.shared.compose.dialog.OmnidroidDialogHandle
import com.omnidroid.app.mobile.shared.compose.dialog.dialogActivity
import com.omnidroid.app.mobile.shared.compose.dialog.showOmnidroidDialog
import com.omnidroid.app.mobile.shared.compose.ui.chromeColors
import com.omnidroid.libretrodroid.LibretroDroid
import com.omnidroid.libretrodroid.TextInputRequest

/** Displays a core's text request with the phone IME, independently of TV/DRC layout. */
internal class NativeSoftwareKeyboard(private val anchor: View) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var dialog: OmnidroidDialogHandle? = null
    private var editor: EditText? = null
    private var shownSession = 0L
    private var dismissedSession = 0L

    private val poll = object : Runnable {
        override fun run() {
            if (!running) return
            if (anchor.isAttachedToWindow) {
                val request = LibretroDroid.getTextInputRequest()
                if (request == null) {
                    dismiss()
                    dismissedSession = 0L
                } else if (request.session != shownSession && request.session != dismissedSession) {
                    show(request)
                }
            }
            handler.postDelayed(this, 150)
        }
    }

    fun start() {
        if (running) return
        running = true
        handler.post(poll)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(poll)
        dismiss()
        dismissedSession = 0L
    }

    /** A dismissed editor can be reopened by touching the game while input is pending. */
    fun reopenIfRequested(): Boolean {
        val request = LibretroDroid.getTextInputRequest() ?: return false
        if (dialog == null) show(request)
        return true
    }

    private fun show(request: TextInputRequest) {
        dismiss()
        val activity = anchor.context.dialogActivity() ?: return
        shownSession = request.session
        val field = EditText(anchor.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            filters = arrayOf(InputFilter.LengthFilter(request.maxLength.coerceAtLeast(0)))
            setText(request.text)
            setSelection(text.length)
            val padding = (12 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        editor = field
        var submitted = false
        fun submit() {
            if (submitted) return
            if (LibretroDroid.updateTextInput(request.session, field.text.toString(), true)) {
                submitted = true
                dismissedSession = request.session
                dismiss()
            }
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!submitted) LibretroDroid.updateTextInput(request.session, s.toString(), false)
            }
        })
        field.setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
            ) {
                submit()
                true
            } else false
        }
        var popup: OmnidroidDialogHandle? = null
        popup = showOmnidroidDialog(
            width = DialogWidth.Input,
            activity = activity,
            title = activity.getString(R.string.game_keyboard_title),
            forceDark = true,
            actions = listOf(
                OmnidroidDialogAction(activity.getString(android.R.string.cancel), DialogActionRole.Cancel) {
                    dismissedSession = request.session
                },
                OmnidroidDialogAction(activity.getString(R.string.game_keyboard_done), dismissOnClick = false) { submit() },
            ),
            onCancel = { dismissedSession = request.session },
            onDismiss = {
                if (dialog === popup) {
                    dialog = null
                    editor = null
                    shownSession = 0L
                }
            },
            content = {
                val colors = chromeColors
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = {
                        field.apply {
                            setTextColor(colors.content.toArgb())
                            setHintTextColor(colors.muted.toArgb())
                            requestFocus()
                            post {
                                if (dialog === popup) {
                                    val ime = anchor.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                    ime.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                                }
                            }
                        }
                    },
                )
            },
        )
        dialog = popup
    }

    private fun dismiss() {
        editor?.let { field ->
            val ime = anchor.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            ime.hideSoftInputFromWindow(field.windowToken, 0)
        }
        dialog?.dismiss()
        dialog = null
        editor = null
        shownSession = 0L
    }
}
