package com.omnidroid.app.shared.deviceprofile

import kotlin.math.cos
import kotlin.math.sin

/**
 * Synthetic CPU workloads timed with [System.nanoTime].
 * Must only be run off the UI thread.
 */
object CpuBenchmark {
    /** Fixed work sized for ~200–500ms on mid-range phones. */
    const val QUICK_ITERATIONS = 1_500_000L

    const val EXTENDED_DURATION_MS = 30_000L

    private const val BATCH_ITERATIONS = 25_000

    fun runQuick(): BenchmarkResult {
        val start = System.nanoTime()
        var result = 0.0
        var i = 0L
        while (i < QUICK_ITERATIONS) {
            val x = i.toDouble()
            result += sin(x) * cos(x)
            i++
        }
        // Prevent the compiler from eliding the loop.
        sink = result
        val elapsedMs = ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
        return BenchmarkResult(
            kind = KIND_QUICK,
            completedAtEpochMs = System.currentTimeMillis(),
            durationMs = elapsedMs,
            iterations = QUICK_ITERATIONS,
            scoreOpsPerSec = QUICK_ITERATIONS * 1000.0 / elapsedMs,
        )
    }

    fun runExtended(
        durationMs: Long = EXTENDED_DURATION_MS,
        onProgress: ((Float) -> Unit)? = null,
    ): BenchmarkResult {
        val startNs = System.nanoTime()
        val endNs = startNs + durationMs * 1_000_000L
        var totalIterations = 0L
        var result = 0.0
        var lastProgressEmitNs = startNs

        while (true) {
            val now = System.nanoTime()
            if (now >= endNs) break

            var i = 0
            while (i < BATCH_ITERATIONS) {
                val x = (totalIterations + i).toDouble()
                result += sin(x) * cos(x)
                i++
            }
            totalIterations += BATCH_ITERATIONS

            if (onProgress != null && now - lastProgressEmitNs >= 250_000_000L) {
                val progress = ((now - startNs).toDouble() / (durationMs * 1_000_000.0)).toFloat()
                onProgress(progress.coerceIn(0f, 1f))
                lastProgressEmitNs = now
            }
        }

        sink = result
        val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
        onProgress?.invoke(1f)
        return BenchmarkResult(
            kind = KIND_EXTENDED,
            completedAtEpochMs = System.currentTimeMillis(),
            durationMs = elapsedMs,
            iterations = totalIterations,
            scoreOpsPerSec = totalIterations * 1000.0 / elapsedMs,
        )
    }

    const val KIND_QUICK = "quick"
    const val KIND_EXTENDED = "extended"

    @Volatile
    private var sink: Double = 0.0
}
