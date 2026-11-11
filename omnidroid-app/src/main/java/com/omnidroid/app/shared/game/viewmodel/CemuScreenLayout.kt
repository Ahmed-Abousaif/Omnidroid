package com.omnidroid.app.shared.game.viewmodel

data class TouchScreenRegion(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object {
        val FULL = TouchScreenRegion(0f, 0f, 1f, 1f)
    }
}

/** Matches the TV/DRC rectangles emitted by Cemu's ScreenLayout.h. */
internal object CemuScreenLayout {
    fun isDual(view: String?): Boolean = view in setOf("side_by_side", "Side by Side", "Side-by-Side", "dual")

    fun nextView(view: String?): String = if (isDual(view)) "tv" else "side_by_side"

    fun nativeLayout(isLandscape: Boolean): String = if (isLandscape) "stacked" else "side_by_side"

    fun aspectRatio(view: String?, isLandscape: Boolean): Float =
        if (isDual(view)) {
            if (isLandscape) 32f / 27f else 32f / 9f
        } else 16f / 9f

    fun touchRegion(view: String?, isLandscape: Boolean): TouchScreenRegion? = when {
        isDual(view) && isLandscape -> TouchScreenRegion(0.25f, 2f / 3f, 0.75f, 1f)
        isDual(view) -> TouchScreenRegion(0.5f, 0f, 1f, 1f)
        view in setOf("pad", "GamePad", "gamepad") -> TouchScreenRegion.FULL
        else -> null
    }
}
