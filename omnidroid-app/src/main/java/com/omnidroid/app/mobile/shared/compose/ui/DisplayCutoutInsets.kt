package com.omnidroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
fun rememberDisplayCutoutInsets(sides: WindowInsetsSides): WindowInsets {
    val cutout = WindowInsets.displayCutout
    return remember(cutout, sides) { cutout.only(sides) }
}

/**
 * Cutout insets for content spanning the full width or top chrome, such as the top bar
 * and the video surface.
 *
 * In landscape, camera cutouts on the side edges are inset to avoid overlap. In portrait,
 * no cutout insets are applied since cutouts do not interfere with portrait layouts.
 */
@Composable
fun rememberDisplayCutoutInsetsForTopBar(isLandscape: Boolean): WindowInsets =
    if (isLandscape) {
        rememberDisplayCutoutInsets(WindowInsetsSides.Horizontal)
    } else {
        WindowInsets(0, 0, 0, 0)
    }

/**
 * Cutout insets for content pinned to a side rail or edge, such as the on-screen pads.
 *
 * In landscape, camera cutouts on the side edges are inset to keep pads clear. In portrait,
 * no cutout insets are applied.
 */
@Composable
fun rememberDisplayCutoutInsetsForEdge(isLandscape: Boolean): WindowInsets =
    if (isLandscape) {
        rememberDisplayCutoutInsets(WindowInsetsSides.Horizontal)
    } else {
        WindowInsets(0, 0, 0, 0)
    }

/** Keeps this content out of the display cutout on [sides]. See [rememberDisplayCutoutInsets]. */
@Composable
fun Modifier.avoidDisplayCutout(sides: WindowInsetsSides): Modifier =
    windowInsetsPadding(rememberDisplayCutoutInsets(sides))