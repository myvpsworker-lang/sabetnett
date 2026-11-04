package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConnectionTimeFormatterTest {
    @Test
    fun formatsSecondsMinutesHours() {
        assertEquals("00:00:00", ConnectionTimeFormatter.format(0))
        assertEquals("00:00:59", ConnectionTimeFormatter.format(59_999))
        assertEquals("00:01:05", ConnectionTimeFormatter.format(65_000))
        assertEquals("01:00:00", ConnectionTimeFormatter.format(3_600_000))
        assertEquals("27:03:09", ConnectionTimeFormatter.format((27 * 3600 + 3 * 60 + 9) * 1000L))
    }

    @Test
    fun negativeIsZero() = assertEquals("00:00:00", ConnectionTimeFormatter.format(-5000))
}
