package com.omnidroid.lib.library.scan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SkippedSubtreeTrackerTest {
    @Test
    fun matchesChildDocumentIds() {
        val tracker = SkippedSubtreeTracker()
        tracker.add("primary:Roms/nes")
        assertTrue(
            tracker.isUnderSkipped(
                "content://com.android.externalstorage.documents/tree/primary%3ARoms/document/primary%3ARoms%2Fnes%2Fmario.nes",
            ),
        )
        assertFalse(
            tracker.isUnderSkipped(
                "content://com.android.externalstorage.documents/tree/primary%3ARoms/document/primary%3ARoms%2Fsnes%2Fzelda.sfc",
            ),
        )
    }

    @Test
    fun matchesExactDirectoryId() {
        val tracker = SkippedSubtreeTracker()
        tracker.add("primary:Roms/nes")
        assertTrue(
            tracker.isUnderSkipped(
                "content://com.android.externalstorage.documents/document/primary%3ARoms%2Fnes",
            ),
        )
    }
}
