package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SubscriptionWarningsTest {
    private val now = 1_000_000_000_000L
    private val rules = RemoteWarnings(enabled = true, expiryDays = 3, lowPercent = 10)
    private fun expireIn(days: Double) = ((now + (days * 86_400_000).toLong()) / 1000)

    @Test
    fun disabledRulesGiveNothing() {
        assertNull(SubscriptionWarnings.compute(SubscriptionUsageInfo(expire = 1), rules.copy(enabled = false), now))
    }

    @Test
    fun expiredToday() {
        assertEquals(SubscriptionWarning.Expired, SubscriptionWarnings.compute(SubscriptionUsageInfo(expire = now / 1000 - 5), rules, now))
        assertEquals(SubscriptionWarning.ExpiresToday, SubscriptionWarnings.compute(SubscriptionUsageInfo(expire = expireIn(0.5)), rules, now))
    }

    @Test
    fun expiresSoonOnlyInsideWindow() {
        assertEquals(SubscriptionWarning.ExpiresIn(2), SubscriptionWarnings.compute(SubscriptionUsageInfo(expire = expireIn(2.5)), rules, now))
        assertNull(SubscriptionWarnings.compute(SubscriptionUsageInfo(expire = expireIn(10.0)), rules, now))
    }

    @Test
    fun lowDataUsesPercentLeft() {
        val low = SubscriptionUsageInfo(download = 95, total = 100)
        assertEquals(SubscriptionWarning.LowData(5), SubscriptionWarnings.compute(low, rules, now))
        assertNull(SubscriptionWarnings.compute(SubscriptionUsageInfo(download = 50, total = 100), rules, now))
        assertNull(SubscriptionWarnings.compute(SubscriptionUsageInfo(download = 999), rules, now))
    }
}
