package com.omnidroid.app.update

import android.content.Context
import okhttp3.OkHttpClient

object UpdaterProvider {
    fun create(
        context: Context,
        @Suppress("UNUSED_PARAMETER") okHttpClient: OkHttpClient,
    ): AppUpdater = PlayAppUpdater(context.applicationContext)
}
