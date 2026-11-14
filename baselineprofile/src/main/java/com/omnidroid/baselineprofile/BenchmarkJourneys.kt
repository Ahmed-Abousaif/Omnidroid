package com.omnidroid.baselineprofile

import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.omnidroid"
internal val benchmarkIterations: Int
    get() = InstrumentationRegistry.getArguments().getString("iterations")?.toIntOrNull()?.coerceIn(3, 15) ?: 5

internal fun MacrobenchmarkScope.startLibrary() {
    startActivityAndWait(
        Intent().setClassName(TARGET_PACKAGE, "com.omnidroid.app.mobile.feature.main.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
    )
    check(device.wait(Until.hasObject(By.res("library-ready")), 15_000)) { "Library did not finish loading" }
}

internal fun MacrobenchmarkScope.startFixture(count: Int, layout: String) {
    startActivityAndWait(
        Intent().setClassName(TARGET_PACKAGE, "com.omnidroid.app.testing.LibraryBenchmarkActivity")
            .putExtra("count", count).putExtra("layout", layout)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
    )
    check(device.wait(Until.hasObject(By.res("benchmark-library-ready")), 15_000)) { "Fixture did not finish loading" }
    SystemClock.sleep(500)
}

internal fun MacrobenchmarkScope.scrollLibrary(vertical: Boolean = false) {
    val width = device.displayWidth
    val height = device.displayHeight
    repeat(6) { index ->
        val forward = index < 3
        if (vertical) {
            device.swipe(width / 2, height * (if (forward) 8 else 2) / 10,
                width / 2, height * (if (forward) 2 else 8) / 10, 24)
        } else {
            device.swipe(width * (if (forward) 8 else 2) / 10, height / 2,
                width * (if (forward) 2 else 8) / 10, height / 2, 24)
        }
        SystemClock.sleep(200)
    }
}

internal fun MacrobenchmarkScope.openGameDetails() {
    val library = checkNotNull(device.findObject(By.res("library-ready")))
    val game = library.findObjects(By.desc(java.util.regex.Pattern.compile(".+")))
        .filter { it.visibleBounds.height() > device.displayHeight / 5 }
        .maxByOrNull { it.visibleBounds.width() * it.visibleBounds.height() }
    checkNotNull(game) { "No game cover was visible in the real library" }.click()
    check(device.wait(Until.hasObject(By.res("game-details-ready")), 10_000)) { "Game details did not open" }
    SystemClock.sleep(600) // Include the full shared-cover/details transition in frame metrics.
}
