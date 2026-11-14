package com.omnidroid.common.view

import android.view.View

fun View.disableTouchEvents() {
    this.setOnTouchListener { _, _ -> true }
}
