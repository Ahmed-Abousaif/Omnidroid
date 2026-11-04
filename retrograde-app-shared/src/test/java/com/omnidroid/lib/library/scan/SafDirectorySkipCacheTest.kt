package com.omnidroid.lib.library.scan

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.omnidroid.lib.library.db.dao.ScanDirectoryDao
import com.omnidroid.lib.library.db.entity.ScanDirectory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SafDirectorySkipCacheTest {
    @Test
    fun evaluateProbeMarksUnreliableWhenMtimesMostlyZero() {
        val cache = cache()
        cache.evaluateProbe("content://tree", listOf(0L, 0L, 0L, 5L))
        assertEquals(SafDirectorySkipCache.Reliability.UNRELIABLE, cache.reliability("content://tree"))
    }

    @Test
    fun evaluateProbeMarksReliableWhenMostMtimesPresent() {
        val cache = cache()
        cache.evaluateProbe("content://tree", listOf(1L, 2L, 3L, 0L))
        assertEquals(SafDirectorySkipCache.Reliability.RELIABLE, cache.reliability("content://tree"))
    }

    @Test
    fun deepCounterForcesEveryNthRun() {
        val cache = cache()
        cache.markReliable("content://tree")
        var forced = 0
        repeat(SafDirectorySkipCache.DEEP_EVERY_N) {
            if (cache.shouldForceDeepThisRun("content://tree")) forced++
        }
        assertEquals(1, forced)
    }

    private fun cache(): SafDirectorySkipCache {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("saf_skip_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return SafDirectorySkipCache(FakeDao(), prefs)
    }

    private class FakeDao : ScanDirectoryDao {
        private val rows = mutableListOf<ScanDirectory>()

        override fun selectByTreeUri(treeUri: String): List<ScanDirectory> =
            rows.filter { it.treeUri == treeUri }

        override fun select(
            treeUri: String,
            documentId: String,
        ): ScanDirectory? = rows.find { it.treeUri == treeUri && it.documentId == documentId }

        override fun upsert(entries: List<ScanDirectory>) {
            for (entry in entries) {
                rows.removeAll { it.treeUri == entry.treeUri && it.documentId == entry.documentId }
                rows += entry
            }
        }

        override fun deleteByTreeUri(treeUri: String) {
            rows.removeAll { it.treeUri == treeUri }
        }

        override fun deleteMissing(
            treeUri: String,
            documentIds: List<String>,
        ) {
            rows.removeAll { it.treeUri == treeUri && it.documentId !in documentIds }
        }
    }
}
