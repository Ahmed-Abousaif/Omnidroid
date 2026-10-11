package com.omnidroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Display-cutout (camera hole) insets for the given [sides].
 *
 * Omnidroid draws edge to edge, so anything pinned to a screen edge - on-screen pads, side
 * rails, full-bleed bars - can end up underneath the hole. The cutout follows the rotation, so
 * the sides a caller needs depend on the current orientation.
 *
 * Devices without a cutout report zero insets, so this is a no-op there.
 */
@Composable
fun rememberDisplayCutoutInsets(sides: WindowInsetsSides): WindowInsets =
    WindowInsets.displayCutout.only(sides)

/**
 * Cutout insets for content pinned to the top or spanning the full width, such as the top bar
 * and the video surface.
 *
 * The hole sits top-centre in portrait and moves to a side edge in landscape, where a full-width
 * bar runs into it at one end.
 */
@Composable
fun rememberDisplayCutoutInsetsForTopBar(isLandscape: Boolean): WindowInsets =
    rememberDisplayCutoutInsets(
        if (isLandscape) WindowInsetsSides.Horizontal else WindowInsetsSides.Top,
    )

/**
 * Cutout insets for content pinned to a side rail or the bottom edge, such as the on-screen
 * pads.
 *
 * A side rail is only ever overlapped in landscape. In portrait the hole is top-centre, far from
 * the rails, and only a hole reaching the bottom edge would reach the pads.
 */
@Composable
fun rememberDisplayCutoutInsetsForEdge(isLandscape: Boolean): WindowInsets =
    rememberDisplayCutoutInsets(
        if (isLandscape) WindowInsetsSides.Horizontal else WindowInsetsSides.Bottom,
    )

/** Keeps this content out of the display cutout on [sides]. See [rememberDisplayCutoutInsets]. */
@Composable
fun Modifier.avoidDisplayCutout(sides: WindowInsetsSides): Modifier =
    windowInsetsPadding(rememberDisplayCutoutInsets(sides))