package com.omnidroid.lib.launch

import android.content.Intent
import android.net.Uri

/**
 * Resolves input URI / path and system hint from an external Intent according to
 * the frontend launch contract.
 */
object ExternalLaunchResolver {

    /**
     * String extras inspected in precedence order if intent.data is absent.
     * Covers DIG, Daijishō (DuckStation, AetherSX2, Dolphin, Citra MMJ conventions).
     */
    val EXTRAS_ORDER = listOf(
        "ROM",
        "bootPath",
        "GamePath",
        "PATH",
        "AutoStartFile",
        "uri",
    )

    const val EXTRA_SYSTEM = "SYSTEM"

    /**
     * Resolves launch input using a data string and an extra getter.
     * Useful for testing without depending on Android Intent mocking.
     */
    fun resolve(
        dataString: String?,
        getExtra: (String) -> String?,
    ): ResolvedLaunchInput? {
        val systemExtra = getExtra(EXTRA_SYSTEM)?.trim()?.takeIf { it.isNotEmpty() }

        // 1. intent.data
        val fromData = dataString?.trim()?.takeIf { it.isNotEmpty() }
        if (fromData != null) {
            val normalized = FileUriNormalizer.normalizeToUriString(fromData)
            val canonical = FileUriNormalizer.getCanonicalPath(fromData)
            return ResolvedLaunchInput(
                uriString = fromData,
                normalizedUriString = normalized,
                canonicalPath = canonical,
                systemExtra = systemExtra,
            )
        }

        // 2. String extras in order
        for (key in EXTRAS_ORDER) {
            val value = getExtra(key)?.trim()?.takeIf { it.isNotEmpty() }
            if (value != null) {
                val normalized = FileUriNormalizer.normalizeToUriString(value)
                val canonical = FileUriNormalizer.getCanonicalPath(value)
                return ResolvedLaunchInput(
                    uriString = value,
                    normalizedUriString = normalized,
                    canonicalPath = canonical,
                    systemExtra = systemExtra,
                )
            }
        }

        return null
    }

    /**
     * Resolves launch input from an Android Intent.
     */
    fun resolve(intent: Intent?): ResolvedLaunchInput? {
        if (intent == null) return null

        val dataString = intent.dataString

        return resolve(dataString) { key ->
            try {
                intent.getStringExtra(key)
                    ?: androidx.core.content.IntentCompat.getParcelableExtra(intent, key, Uri::class.java)?.toString()
            } catch (e: Throwable) {
                null
            }
        }
    }
}
