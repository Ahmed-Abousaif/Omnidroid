package com.omnidroid.app.update

import android.content.Context
import okhttp3.OkHttpClient

object UpdaterProvider {
    fun create(
        context: Context,
        okHttpClient: OkHttpClient,
    ): AppUpdater = GitHubAppUpdater(context.applicationContext, okHttpClient)
}
