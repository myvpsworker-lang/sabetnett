package com.v2ray.ang.handler

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FastestServerPickerTest {
    private fun c(id: String) = FastestServerPicker.Candidate(id, "h", 1)

    @Test
    fun chooseTakesLowestAndIgnoresFailures() {
        assertEquals("b", FastestServerPicker.choose(listOf("a" to 90L, "b" to 20L, "c" to null)))
        assertNull(FastestServerPicker.choose(listOf("a" to null)))
        assertNull(FastestServerPicker.choose(emptyList()))
    }

    @Test
    fun tieKeepsEarlierCandidate() {
        assertEquals("a", FastestServerPicker.choose(listOf("a" to 10L, "b" to 10L)))
    }

    @Test
    fun pickUsesProbeResults() = runBlocking {
        val times = mapOf("a" to 80L, "b" to 15L, "c" to null)
        val best = FastestServerPicker.pick(listOf(c("a"), c("b"), c("c"))) { times[it.guid] }
        assertEquals("b", best)
    }

    @Test
    fun emptyListGivesNull() = runBlocking {
        assertNull(FastestServerPicker.pick(emptyList()) { 1L })
    }

    @Test
    fun slowProbesFallBackToNullWithinBudget() = runBlocking {
        val best = FastestServerPicker.pick(listOf(c("a"))) { delay(60_000); 1L }
        assertNull(best)
    }
}
