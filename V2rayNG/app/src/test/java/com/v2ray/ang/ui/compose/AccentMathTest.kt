package com.v2ray.ang.ui.compose

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccentMathTest {
    @Test
    fun mixEndpointsAndMiddle() {
        assertEquals(0xFF000000.toInt(), AccentMath.mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0f))
        assertEquals(0xFFFFFFFF.toInt(), AccentMath.mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 1f))
        assertEquals(0xFF7F7F7F.toInt(), AccentMath.mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f))
    }

    @Test
    fun onColorPicksReadableText() {
        assertEquals(0xFF000000.toInt(), AccentMath.onColor(0xFFFFFF00.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), AccentMath.onColor(0xFF1D4ED8.toInt()))
    }

    @Test
    fun lightPrimaryIsTheAccentItself() {
        val p = AccentMath.primaryFor(0xFF059669.toInt(), dark = false)
        assertEquals(0xFF059669.toInt(), p.primary)
        assertTrue(AccentMath.luminance(p.container) > AccentMath.luminance(p.primary))
    }

    @Test
    fun darkPrimaryIsLighterThanTheAccentAndContainerDarker() {
        val a = 0xFF2563EB.toInt()
        val p = AccentMath.primaryFor(a, dark = true)
        assertTrue(AccentMath.luminance(p.primary) > AccentMath.luminance(a))
        assertTrue(AccentMath.luminance(p.container) < AccentMath.luminance(a))
        assertEquals(a, p.inverse)
    }
}
