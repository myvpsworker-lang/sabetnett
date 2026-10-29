package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TrafficSpeedCalculatorTest {
    @Test
    fun computesPerSecondAndSessionTotals() {
        val s = TrafficSpeedCalculator.speed(TrafficSample(0, 0), TrafficSample(1000, 100), TrafficSample(3000, 300), 2000)!!
        assertEquals(1000, s.downBytesPerSec)
        assertEquals(100, s.upBytesPerSec)
        assertEquals(3000, s.sessionDownBytes)
        assertEquals(300, s.sessionUpBytes)
    }

    @Test
    fun counterResetNeverGivesNegativeSpeed() {
        val s = TrafficSpeedCalculator.speed(TrafficSample(500, 500), TrafficSample(900, 900), TrafficSample(10, 10), 1000)!!
        assertEquals(0, s.downBytesPerSec)
        assertEquals(0, s.sessionDownBytes)
    }

    @Test
    fun unsupportedCountersOrBadIntervalGiveNull() {
        val ok = TrafficSample(1, 1)
        assertNull(TrafficSpeedCalculator.speed(ok, ok, TrafficSample(-1, -1), 1000))
        assertNull(TrafficSpeedCalculator.speed(ok, ok, ok, 0))
    }
}
