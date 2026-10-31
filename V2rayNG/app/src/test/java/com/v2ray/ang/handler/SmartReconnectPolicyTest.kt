package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmartReconnectPolicyTest {
    @Test
    fun switchesOnlyAfterConsecutiveFailures() {
        val p = SmartReconnectPolicy(failuresToSwitch = 3, cooldownMs = 1000)
        assertFalse(p.onProbe(false, 0))
        assertFalse(p.onProbe(false, 10))
        assertTrue(p.onProbe(false, 20))
    }

    @Test
    fun aSuccessResetsTheStreak() {
        val p = SmartReconnectPolicy(failuresToSwitch = 3, cooldownMs = 1000)
        p.onProbe(false, 0); p.onProbe(false, 1)
        assertFalse(p.onProbe(true, 2))
        assertFalse(p.onProbe(false, 3)); assertFalse(p.onProbe(false, 4))
        assertTrue(p.onProbe(false, 5))
    }

    @Test
    fun cooldownBlocksRepeatedSwitches() {
        val p = SmartReconnectPolicy(failuresToSwitch = 1, cooldownMs = 1000)
        assertTrue(p.onProbe(false, 100))
        assertFalse(p.onProbe(false, 500), "inside cooldown")
        assertTrue(p.onProbe(false, 1100), "cooldown over")
    }

    @Test
    fun resetForgetsFailuresButKeepsCooldown() {
        val p = SmartReconnectPolicy(failuresToSwitch = 2, cooldownMs = 1000)
        p.onProbe(false, 0)
        p.reset()
        assertFalse(p.onProbe(false, 1))
        assertTrue(p.onProbe(false, 2))
        p.reset()
        p.onProbe(false, 3)
        assertFalse(p.onProbe(false, 4), "still cooling down")
        assertEquals(false, p.onProbe(true, 5))
    }
}
