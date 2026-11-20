package com.omnidroid.app.mobile.shared.compose.ui

import kotlin.math.pow

/** WCAG contrast maths on packed ARGB colours, kept free of Compose so it can be unit tested. */
internal object AccentContrast {
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    /** WCAG relative luminance, 0 (black) to 1 (white). */
    fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** WCAG contrast ratio, 1 to 21. */
    fun contrast(
        a: Int,
        b: Int,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Black or white, whichever reads better as text on [argb]. */
    fun onColor(argb: Int): Int = if (contrast(argb, BLACK) >= contrast(argb, WHITE)) BLACK else WHITE
}
