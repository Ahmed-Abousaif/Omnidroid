package com.omnidroid.app.utils.android

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.omnidroid.BuildConfig

/** Readiness markers for the external benchmark runner; absent in shipping builds. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.benchmarkTag(tag: String): Modifier =
    if (BuildConfig.BUILD_TYPE == "benchmarkRelease" || BuildConfig.BUILD_TYPE == "nonMinifiedRelease") {
        semantics { testTagsAsResourceId = true }.testTag(tag)
    } else this
