package com.omnidroid.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Captures startup, details navigation, and the real renderers with local-cover fixtures. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun startup() {
        rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
            pressHome()
            startLibrary()
        }
    }

    @Test
    fun library() {
        rule.collect(
            packageName = TARGET_PACKAGE,
            filterPredicate = { !it.contains("Lcom/omnidroid/app/testing/") },
        ) {
            pressHome()
            startLibrary()
            openGameDetails()
            device.pressBack()
            startFixture(1_000, "GRID")
            scrollLibrary()
            startFixture(1_000, "LIST")
            scrollLibrary(vertical = true)
            startFixture(1_000, "CONSOLE")
            scrollLibrary()
        }
    }
}
