package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.SubscriptionUsageInfo

/** Reason to warn the user about their subscription, in priority order. */
sealed interface SubscriptionWarning {
    data object Expired : SubscriptionWarning
    data object ExpiresToday : SubscriptionWarning
    data class ExpiresIn(val days: Int) : SubscriptionWarning
    data class LowData(val percentLeft: Int) : SubscriptionWarning
}

/** Pure decision for the warning card shown above a subscription's server list. */
object SubscriptionWarnings {
    private const val MILLIS_PER_DAY = 86_400_000L

    fun compute(info: SubscriptionUsageInfo, rules: RemoteWarnings, nowMs: Long): SubscriptionWarning? {
        if (!rules.enabled) return null
        if (info.expire > 0) {
            val remaining = info.expire * 1000L - nowMs
            if (remaining <= 0) return SubscriptionWarning.Expired
            if (rules.expiryDays > 0) {
                val days = (remaining / MILLIS_PER_DAY).toInt()
                if (days == 0) return SubscriptionWarning.ExpiresToday
                if (days <= rules.expiryDays) return SubscriptionWarning.ExpiresIn(days)
            }
        }
        if (info.total > 0 && rules.lowPercent > 0) {
            val left = (info.total - info.used).coerceAtLeast(0)
            val percent = (left * 100 / info.total).toInt().coerceIn(0, 100)
            if (percent <= rules.lowPercent) return SubscriptionWarning.LowData(percent)
        }
        return null
    }
}
