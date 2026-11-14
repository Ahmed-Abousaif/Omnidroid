package com.omnidroid.lib.core

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AssetRetryTest {
    @Test fun transientNetworkFailureCanRecover() = runBlocking {
        var attempts = 0
        retryAssetInstall { if (++attempts < 2) throw IOException("interrupted") }
        assertEquals(2, attempts)
    }

    @Test fun persistentFailureIsReportedAfterABoundedNumberOfAttempts() = runBlocking {
        var attempts = 0
        try {
            retryAssetInstall {
                attempts++
                throw IOException("offline")
            }
            fail("Expected an installation error")
        } catch (_: IOException) {
            assertEquals(3, attempts)
        }
    }

    @Test fun cancellationIsNeverRetriedOrSwallowed() = runBlocking {
        var attempts = 0
        try {
            retryAssetInstall {
                attempts++
                throw CancellationException("cancelled")
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, attempts)
        }
    }
}
