package com.omnidroid.app.shared.game

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Shared factory for constructing canonical external launch intents.
 * Used by Android TV preview programs, pin-to-home shortcuts, and test harnesses.
 */
object ExternalLaunchIntentBuilder {
    const val MIME_TYPE = "application/octet-stream"
    const val TARGET_CLASS = "com.omnidroid.app.shared.game.ExternalGameLauncherActivity"

    /**
     * Builds a canonical ACTION_VIEW intent targeting ExternalGameLauncherActivity for the given fileUri.
     */
    fun forFile(context: Context, fileUri: String): Intent {
        return forFile(fileUri, context.packageName)
    }

    /**
     * Builds a canonical ACTION_VIEW intent targeting ExternalGameLauncherActivity with explicit package name.
     */
    fun forFile(fileUri: String, packageName: String = "com.omnidroid"): Intent {
        val uri = Uri.parse(fileUri)
        return Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(packageName, TARGET_CLASS)
            addCategory(Intent.CATEGORY_DEFAULT)
            setDataAndType(uri, MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
