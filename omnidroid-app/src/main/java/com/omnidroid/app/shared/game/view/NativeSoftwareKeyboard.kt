package com.omnidroid.app.shared.game.view

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.omnidroid.R
import com.omnidroid.libretrodroid.LibretroDroid
import com.omnidroid.libretrodroid.TextInputRequest

/** Displays a core's text request with the phone IME, independently of TV/DRC layout. */
internal class NativeSoftwareKeyboard(private val anchor: View) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var dialog: AlertDialog? = null
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
        shownSession = request.session
        val field = EditText(anchor.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            filters = arrayOf(InputFilter.LengthFilter(request.maxLength.coerceAtLeast(0)))
            setText(request.text)
            setSelection(text.length)
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        editor = field
        val popup = AlertDialog.Builder(anchor.context)
            .setTitle(R.string.game_keyboard_title)
            .setView(field)
            .setPositiveButton(R.string.game_keyboard_done, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> dismissedSession = request.session }
            .create()
        dialog = popup
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
        popup.setOnCancelListener { dismissedSession = request.session }
        popup.setOnDismissListener {
            if (dialog === popup) {
                dialog = null
                editor = null
                shownSession = 0L
            }
        }
        popup.setOnShowListener {
            popup.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() }
            field.requestFocus()
            field.post {
                if (dialog === popup) {
                    val ime = anchor.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    ime.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
        popup.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        )
        popup.show()
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
