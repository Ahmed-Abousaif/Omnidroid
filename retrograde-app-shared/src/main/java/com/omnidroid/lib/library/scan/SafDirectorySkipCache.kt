package com.omnidroid.lib.library.scan

import android.content.SharedPreferences
import com.omnidroid.lib.library.db.dao.ScanDirectoryDao
import com.omnidroid.lib.library.db.entity.ScanDirectory
import timber.log.Timber

/**
 * Persists per-directory LAST_MODIFIED for SAF trees and tracks whether the provider's
 * directory mtimes are trustworthy enough to skip unchanged subtrees.
 */
class SafDirectorySkipCache(
    private val dao: ScanDirectoryDao,
    private val preferences: SharedPreferences,
) {
    enum class Reliability {
        UNKNOWN,
        RELIABLE,
        UNRELIABLE,
    }

    fun reliability(treeUri: String): Reliability {
        return when (preferences.getString(reliabilityKey(treeUri), null)) {
            VALUE_RELIABLE -> Reliability.RELIABLE
            VALUE_UNRELIABLE -> Reliability.UNRELIABLE
            else -> Reliability.UNKNOWN
        }
    }

    fun markReliable(treeUri: String) {
        preferences.edit().putString(reliabilityKey(treeUri), VALUE_RELIABLE).apply()
    }

    fun markUnreliable(treeUri: String) {
        Timber.w("SAF directory mtime marked unreliable for $treeUri")
        preferences.edit().putString(reliabilityKey(treeUri), VALUE_UNRELIABLE).apply()
        dao.deleteByTreeUri(treeUri)
    }

    fun cachedMtime(
        treeUri: String,
        documentId: String,
    ): Long? = dao.select(treeUri, documentId)?.lastModified

    fun upsert(
        treeUri: String,
        documentId: String,
        lastModified: Long,
        now: Long = System.currentTimeMillis(),
    ) {
        if (lastModified <= 0L) return
        dao.upsert(
            listOf(
                ScanDirectory(
                    treeUri = treeUri,
                    documentId = documentId,
                    lastModified = lastModified,
                    probedAt = now,
                ),
            ),
        )
    }

    fun upsertAll(
        treeUri: String,
        entries: Map<String, Long>,
        now: Long = System.currentTimeMillis(),
    ) {
        if (entries.isEmpty()) return
        dao.upsert(
            entries.map { (documentId, lastModified) ->
                ScanDirectory(
                    treeUri = treeUri,
                    documentId = documentId,
                    lastModified = lastModified,
                    probedAt = now,
                )
            },
        )
    }

    fun pruneMissing(
        treeUri: String,
        presentDocumentIds: Collection<String>,
    ) {
        if (presentDocumentIds.isEmpty()) {
            dao.deleteByTreeUri(treeUri)
            return
        }
        dao.deleteMissing(treeUri, presentDocumentIds.toList())
    }

    /**
     * After a full (deep) walk, decide reliability from sampled directory mtimes.
     * Providers that mostly report 0 are marked unreliable (fail-safe: always walk).
     */
    fun evaluateProbe(
        treeUri: String,
        directoryMtimes: Collection<Long>,
    ) {
        if (directoryMtimes.isEmpty()) return
        val nonZero = directoryMtimes.count { it > 0L }
        val ratio = nonZero.toDouble() / directoryMtimes.size
        if (ratio < RELIABLE_NONZERO_RATIO) {
            markUnreliable(treeUri)
        } else if (reliability(treeUri) != Reliability.UNRELIABLE) {
            markReliable(treeUri)
        }
    }

    /**
     * Deep revalidation: parent mtime unchanged in cache but a child file's size/mtime
     * changed vs the game index → provider lies about directory mtimes.
     */
    fun reportSkipCacheMiss(treeUri: String) {
        markUnreliable(treeUri)
    }

    fun shouldForceDeepThisRun(treeUri: String): Boolean {
        if (reliability(treeUri) != Reliability.RELIABLE) return true
        val count = preferences.getInt(deepCounterKey(treeUri), 0) + 1
        preferences.edit().putInt(deepCounterKey(treeUri), count % DEEP_EVERY_N).apply()
        return count % DEEP_EVERY_N == 0
    }

    private fun reliabilityKey(treeUri: String) = "saf_mtime_reliable:$treeUri"

    private fun deepCounterKey(treeUri: String) = "saf_deep_scan_counter:$treeUri"

    companion object {
        private const val VALUE_RELIABLE = "reliable"
        private const val VALUE_UNRELIABLE = "unreliable"
        private const val RELIABLE_NONZERO_RATIO = 0.5
        const val DEEP_EVERY_N = 10
    }
}
