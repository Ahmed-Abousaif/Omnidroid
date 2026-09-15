package com.omnidroid.app.tv.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.omnidroid.app.mobile.feature.main.MainActivity

class MainTVActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = intent.action
                data = intent.data
                putExtras(intent)
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
        )
        finish()
    }
}
