package com.omnidroid.app.shared.main

import android.app.Activity

interface BusyActivity {
    fun activity(): Activity

    fun isBusy(): Boolean
}
