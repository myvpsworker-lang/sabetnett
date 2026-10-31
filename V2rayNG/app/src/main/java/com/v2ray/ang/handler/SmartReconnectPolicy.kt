package com.v2ray.ang.handler

/**
 * Decides when an unstable connection should move to another server: [failuresToSwitch] failed health
 * probes in a row, and not again within [cooldownMs] of the previous switch so a bad network cannot
 * make the app restart the tunnel in a loop. Pure and clock-injected so it is unit-testable.
 */
class SmartReconnectPolicy(
    private val failuresToSwitch: Int = 3,
    private val cooldownMs: Long = 120_000L,
) {
    private var failures = 0
    private var lastSwitchAtMs: Long? = null

    /** Records one probe result and returns true when the caller should now switch servers. */
    fun onProbe(ok: Boolean, nowMs: Long): Boolean {
        if (ok) {
            failures = 0
            return false
        }
        failures++
        if (failures < failuresToSwitch) return false
        val last = lastSwitchAtMs
        if (last != null && nowMs - last < cooldownMs) return false
        failures = 0
        lastSwitchAtMs = nowMs
        return true
    }

    /** Forgets the failure streak (connection stopped or restarted); the cooldown is kept. */
    fun reset() {
        failures = 0
    }
}
