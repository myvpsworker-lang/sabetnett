package com.v2ray.ang.handler

/** Formats how long the tunnel has been up as `HH:MM:SS`; hours keep counting past 24 and never go negative. */
object ConnectionTimeFormatter {
    fun format(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs.coerceAtLeast(0L)) / 1000L
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }
}
