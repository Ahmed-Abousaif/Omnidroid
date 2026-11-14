package com.omnidroid.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class LibraryBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun grid1000() = scrolling(1_000, "GRID")
    @Test fun grid5000() = scrolling(5_000, "GRID")
    @Test fun list5000() = scrolling(5_000, "LIST")
    @Test fun console5000() = scrolling(5_000, "CONSOLE")

    @Test
    fun openDetails() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Max)),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Disable, warmupIterations = 3),
        iterations = benchmarkIterations,
        setupBlock = { killProcess(); startLibrary() },
        measureBlock = { openGameDetails() },
    )

    private fun scrolling(count: Int, layout: String) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Max)),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Disable, warmupIterations = 3),
        iterations = benchmarkIterations,
        setupBlock = { killProcess(); startFixture(count, layout) },
        measureBlock = { scrollLibrary(vertical = layout == "LIST") },
    )

}
