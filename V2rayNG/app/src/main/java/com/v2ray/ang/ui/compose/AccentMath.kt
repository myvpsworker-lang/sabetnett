package com.v2ray.ang.ui.compose

/** Plain-integer colour maths for the admin-chosen accent, kept free of Compose types so it is unit-testable. */
object AccentMath {
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()

    /** Linear mix of two opaque ARGB colours; [t] = 0 gives [a], 1 gives [b]. */
    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val x = (a ushr shift) and 0xFF
            val y = (b ushr shift) and 0xFF
            return (x + (y - x) * k).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Relative luminance (WCAG) of an opaque colour, 0 = black, 1 = white. */
    fun luminance(argb: Int): Double {
        fun lin(shift: Int): Double {
            val c = ((argb ushr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(16) + 0.7152 * lin(8) + 0.0722 * lin(0)
    }

    /** Black or white, whichever reads better on [argb]. */
    fun onColor(argb: Int): Int = if (luminance(argb) > 0.4) BLACK else WHITE

    /** The five primary-family colours derived from [accent] for a light or dark surface. */
    data class Primary(val primary: Int, val onPrimary: Int, val container: Int, val onContainer: Int, val inverse: Int)

    fun primaryFor(accent: Int, dark: Boolean): Primary =
        if (dark) {
            val primary = mix(accent, WHITE, 0.4f)
            Primary(primary, onColor(primary), mix(accent, BLACK, 0.45f), mix(accent, WHITE, 0.85f), accent)
        } else {
            Primary(accent, onColor(accent), mix(accent, WHITE, 0.82f), mix(accent, BLACK, 0.75f), mix(accent, WHITE, 0.5f))
        }
}
