package com.omnidroid.lib.library.scan

import android.net.Uri
import android.provider.DocumentsContract

/**
 * Tracks SAF document IDs whose subtrees were skipped via mtime cache, so the library
 * diff does not treat those games as removed.
 */
class SkippedSubtreeTracker {
    private val skippedDocumentIds = linkedSetOf<String>()

    fun clear() {
        skippedDocumentIds.clear()
    }

    fun add(documentId: String) {
        skippedDocumentIds += documentId
    }

    fun snapshot(): Set<String> = skippedDocumentIds.toSet()

    fun isUnderSkipped(fileUri: String): Boolean {
        if (skippedDocumentIds.isEmpty()) return false
        val docId =
            runCatching { DocumentsContract.getDocumentId(Uri.parse(fileUri)) }
                .getOrNull()
                ?: return false
        return skippedDocumentIds.any { skipped ->
            docId == skipped || docId.startsWith("$skipped/")
        }
    }

    companion object {
        /** Process-wide last listing skips for the SAF provider (consumed by OmnidroidLibrary). */
        @Volatile
        var lastSkipped: Set<String> = emptySet()
    }
}
