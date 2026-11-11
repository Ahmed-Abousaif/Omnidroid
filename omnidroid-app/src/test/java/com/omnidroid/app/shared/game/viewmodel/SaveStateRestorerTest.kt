package com.omnidroid.app.shared.game.viewmodel

import com.omnidroid.lib.saves.IncompatibleStateException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SaveStateRestorerTest {
    @Test
    fun stopsAfterSuccessfulRestore() =
        runBlocking {
            var calls = 0
            restoreSaveStateWithRetry { ++calls == 3 }
            assertEquals(3, calls)
        }

    @Test
    fun reportsExhaustedRetriesInsteadOfReturningSuccess() =
        runBlocking {
            var calls = 0
            val result =
                runCatching {
                    restoreSaveStateWithRetry {
                        calls++
                        false
                    }
                }
            assertTrue(result.exceptionOrNull() is IOException)
            assertEquals(10, calls)
        }

    @Test
    fun incompatibleStateFailsImmediately() =
        runBlocking {
            var calls = 0
            val result =
                runCatching {
                    restoreSaveStateWithRetry {
                        calls++
                        throw IncompatibleStateException()
                    }
                }
            assertTrue(result.exceptionOrNull() is IncompatibleStateException)
            assertEquals(1, calls)
        }

    @Test
    fun cancellationIsPropagatedWithoutRetrying() =
        runBlocking {
            var calls = 0
            val result =
                runCatching {
                    restoreSaveStateWithRetry {
                        calls++
                        throw CancellationException("stopped")
                    }
                }
            assertTrue(result.exceptionOrNull() is CancellationException)
            assertEquals(1, calls)
        }
}
