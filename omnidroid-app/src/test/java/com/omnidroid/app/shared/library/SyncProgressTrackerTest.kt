package com.omnidroid.app.shared.library

import com.omnidroid.lib.core.SyncProgressTracker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SyncProgressTrackerTest {
    @Before fun prepare() = SyncProgressTracker.clear()

    @After fun clean() = SyncProgressTracker.clear()

    @Test fun quietCheckFinishingDoesNotHideAnActualDownload() {
        val download = SyncProgressTracker.session()
        val cachedCheck = SyncProgressTracker.session()
        download.update("Downloading core")
        cachedCheck.clear()
        assertEquals("Downloading core", SyncProgressTracker.progress.value?.stepText)
        download.clear()
        assertNull(SyncProgressTracker.progress.value)
    }

    @Test fun previousWorkerFinishingDoesNotClearTheNextWorkersProgress() {
        val scan = SyncProgressTracker.session()
        val download = SyncProgressTracker.session()
        scan.update("Scanning")
        download.update("Downloading core")
        scan.clear()
        assertEquals("Downloading core", SyncProgressTracker.progress.value?.stepText)
        download.clear()
        assertNull(SyncProgressTracker.progress.value)
    }

    @Test fun identicalMessagesStillBelongToTheLatestOperation() {
        val first = SyncProgressTracker.session()
        val second = SyncProgressTracker.session()
        first.update("Downloading core")
        second.update("Downloading core")
        first.clear()
        assertEquals("Downloading core", SyncProgressTracker.progress.value?.stepText)
        second.clear()
        assertNull(SyncProgressTracker.progress.value)
    }

    @Test fun explicitResetClearsTheCurrentOwnerAndAllowsAnotherOperation() {
        val old = SyncProgressTracker.session()
        old.update("Downloading core")
        SyncProgressTracker.clear()
        assertNull(SyncProgressTracker.progress.value)
        val current = SyncProgressTracker.session()
        current.update("Checking assets")
        old.clear()
        assertEquals("Checking assets", SyncProgressTracker.progress.value?.stepText)
    }
}
