package com.v2ray.ang.handler

/** Cumulative bytes received/sent by this app's UID; negative values mean the device does not report them. */
data class TrafficSample(val rxBytes: Long, val txBytes: Long) {
    val supported: Boolean get() = rxBytes >= 0 && txBytes >= 0
}

/** Bytes per second since the previous sample plus the totals since the connection started. */
data class TrafficSpeed(val downBytesPerSec: Long, val upBytesPerSec: Long, val sessionDownBytes: Long, val sessionUpBytes: Long)

object TrafficSpeedCalculator {
    /**
     * Speed between [previous] and [current] taken [elapsedMs] apart, relative to [start]. Counters that
     * went backwards (device reset) count as zero instead of a negative speed. Returns null when the
     * device does not support the counters.
     */
    fun speed(start: TrafficSample, previous: TrafficSample, current: TrafficSample, elapsedMs: Long): TrafficSpeed? {
        if (!start.supported || !previous.supported || !current.supported || elapsedMs <= 0) return null
        val down = (current.rxBytes - previous.rxBytes).coerceAtLeast(0)
        val up = (current.txBytes - previous.txBytes).coerceAtLeast(0)
        return TrafficSpeed(
            downBytesPerSec = down * 1000 / elapsedMs,
            upBytesPerSec = up * 1000 / elapsedMs,
            sessionDownBytes = (current.rxBytes - start.rxBytes).coerceAtLeast(0),
            sessionUpBytes = (current.txBytes - start.txBytes).coerceAtLeast(0),
        )
    }
}
