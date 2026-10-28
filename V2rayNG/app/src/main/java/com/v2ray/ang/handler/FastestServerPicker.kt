package com.v2ray.ang.handler

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Picks the server whose TCP handshake completes fastest. This is a quick pre-connect probe, not a
 * replacement for the real-delay test: UDP-only protocols do not answer and are simply not chosen.
 */
object FastestServerPicker {
    data class Candidate(val guid: String, val host: String, val port: Int)

    private const val PROBE_TIMEOUT_MS = 2500L
    private const val TOTAL_BUDGET_MS = 4000L
    private const val MAX_CANDIDATES = 40

    /** Returns the guid with the lowest handshake time, or null when none answered in time. */
    suspend fun pick(
        candidates: List<Candidate>,
        probe: suspend (Candidate) -> Long? = { c -> tcpProbe(c) },
    ): String? {
        if (candidates.isEmpty()) return null
        val results = withTimeoutOrNull(TOTAL_BUDGET_MS) {
            coroutineScope {
                candidates.take(MAX_CANDIDATES).map { c -> async { c.guid to probe(c) } }.awaitAll()
            }
        } ?: return null
        return choose(results)
    }

    /** Lowest positive time wins; ties keep the earlier (list-order) candidate. */
    fun choose(results: List<Pair<String, Long?>>): String? =
        results.filter { (_, ms) -> ms != null && ms >= 0 }.minByOrNull { it.second!! }?.first

    private suspend fun tcpProbe(c: Candidate): Long? = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
        try {
            val start = System.nanoTime()
            Socket().use { it.connect(InetSocketAddress(c.host, c.port), PROBE_TIMEOUT_MS.toInt()) }
            (System.nanoTime() - start) / 1_000_000
        } catch (e: Exception) {
            null
        }
    }
}
