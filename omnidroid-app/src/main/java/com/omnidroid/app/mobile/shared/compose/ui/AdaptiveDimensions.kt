package com.omnidroid.app.mobile.shared.compose.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-performance, zero-allocation adaptive layout specification.
 * Marked @Immutable to allow Compose compiler to skip recompositions.
 */
@Immutable
data class AdaptiveLayoutSpec(
    val isCompact: Boolean,
    val isSquare: Boolean,
    val isUltraCompact: Boolean,
    val sidebarWidth: Dp,
    val sidebarButtonSize: Dp,
    val sidebarIconSize: Dp,
    val consoleBarHeight: Dp,
    val searchWidth: Dp,
    val topBarRowHeight: Dp,
    val topBarPortraitHeight: Dp,
    val listThumbHeight: Dp,
    val gridSpacing: Dp,
)

// Pre-allocated static singletons to ensure zero heap allocations during layout calculation.
private val UltraCompactSpec = AdaptiveLayoutSpec(
    isCompact = true,
    isSquare = false,
    isUltraCompact = true,
    sidebarWidth = 46.dp,
    sidebarButtonSize = 34.dp,
    sidebarIconSize = 18.dp,
    consoleBarHeight = 48.dp,
    searchWidth = 110.dp,
    topBarRowHeight = 34.dp,
    topBarPortraitHeight = 68.dp,
    listThumbHeight = 46.dp,
    gridSpacing = 8.dp,
)

private val SquareCompactSpec = AdaptiveLayoutSpec(
    isCompact = true,
    isSquare = true,
    isUltraCompact = false,
    sidebarWidth = 54.dp,
    sidebarButtonSize = 38.dp,
    sidebarIconSize = 22.dp,
    consoleBarHeight = 52.dp,
    searchWidth = 130.dp,
    topBarRowHeight = 34.dp,
    topBarPortraitHeight = 68.dp,
    listThumbHeight = 46.dp,
    gridSpacing = 8.dp,
)

private val CompactSpec = AdaptiveLayoutSpec(
    isCompact = true,
    isSquare = false,
    isUltraCompact = false,
    sidebarWidth = 56.dp,
    sidebarButtonSize = 40.dp,
    sidebarIconSize = 22.dp,
    consoleBarHeight = 56.dp,
    searchWidth = 136.dp,
    topBarRowHeight = 34.dp,
    topBarPortraitHeight = 68.dp,
    listThumbHeight = 46.dp,
    gridSpacing = 8.dp,
)

private val SquareStandardSpec = AdaptiveLayoutSpec(
    isCompact = false,
    isSquare = true,
    isUltraCompact = false,
    sidebarWidth = 64.dp,
    sidebarButtonSize = 44.dp,
    sidebarIconSize = 24.dp,
    consoleBarHeight = 60.dp,
    searchWidth = 160.dp,
    topBarRowHeight = 38.dp,
    topBarPortraitHeight = 76.dp,
    listThumbHeight = 52.dp,
    gridSpacing = 10.dp,
)

private val StandardSpec = AdaptiveLayoutSpec(
    isCompact = false,
    isSquare = false,
    isUltraCompact = false,
    sidebarWidth = 80.dp,
    sidebarButtonSize = 48.dp,
    sidebarIconSize = 26.dp,
    consoleBarHeight = 68.dp,
    searchWidth = 200.dp,
    topBarRowHeight = 40.dp,
    topBarPortraitHeight = 80.dp,
    listThumbHeight = 56.dp,
    gridSpacing = 12.dp,
)

/**
 * Ultra-fast layout calculator using primitive float comparisons and early-exit branching.
 * Zero object allocation and zero garbage collection overhead.
 */
fun calculateAdaptiveLayoutSpec(width: Dp, height: Dp): AdaptiveLayoutSpec {
    val w = width.value
    val h = height.value

    if (w <= 0f || h <= 0f) return StandardSpec
    if (w < 380f || h < 320f) return UltraCompactSpec

    val aspectRatio = w / h
    val isSquare = aspectRatio >= 0.85f && aspectRatio <= 1.18f

    return if (isSquare) {
        if (w < 640f || h < 400f) SquareCompactSpec else SquareStandardSpec
    } else {
        if (w < 540f || h < 400f) CompactSpec else StandardSpec
    }
}

/**
 * staticCompositionLocalOf is used for maximum performance because adaptive layout
 * spec changes only upon screen rotation/resize, not on every frame.
 */
val LocalAdaptiveLayout = staticCompositionLocalOf {
    StandardSpec
}
