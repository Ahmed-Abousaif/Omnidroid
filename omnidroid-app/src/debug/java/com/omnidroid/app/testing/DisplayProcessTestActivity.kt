package com.omnidroid.app.testing

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.widget.TextView

/** A native-core-free host for exercising the real :game process lifecycle in instrumentation. */
class DisplayProcessTestActivity : Activity() {
    private val endpoint =
        Messenger(
            Handler(Looper.getMainLooper()) {
                finish()
                true
            },
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Display process test" })
    }

    @Suppress("DEPRECATION")
    override fun onResume() {
        super.onResume()
        window.decorView.post {
            intent.getParcelableExtra<Messenger>("ready")?.send(
                Message.obtain(null, 0).also {
                    it.replyTo = endpoint
                },
            )
        }
    }
}
