package com.v2ray.ang.handler

/** The one remote message dialog to show right now; more than one at a time would stack up on launch. */
sealed interface RemoteDialog {
    data object None : RemoteDialog
    data object Onboarding : RemoteDialog
    data class Emergency(val message: RemoteEmergency) : RemoteDialog
    data class WhatsNew(val info: RemoteWhatsNew) : RemoteDialog
    data class Announcement(val announcement: RemoteAnnouncement) : RemoteDialog
}

object RemoteDialogPicker {
    /**
     * Priority: first-run slides, emergency message (again on every launch until switched off, so only
     * the id closed in this session is skipped), what's new after an update, then the announcement.
     */
    fun pick(
        config: RemoteConfig,
        dismissed: RemoteDismissed,
        emergencyClosedThisSession: Int,
        installedVersionCode: Int,
    ): RemoteDialog {
        if (config.features.onboarding && config.onboarding.isNotEmpty() && !dismissed.onboardingSeen) return RemoteDialog.Onboarding
        config.emergency?.takeIf { it.id != emergencyClosedThisSession }?.let { return RemoteDialog.Emergency(it) }
        config.whatsNew
            ?.takeIf { installedVersionCode >= it.versionCode && dismissed.whatsNewSeen < it.versionCode }
            ?.let { return RemoteDialog.WhatsNew(it) }
        config.announcement?.takeIf { it.id != dismissed.announcementId }?.let { return RemoteDialog.Announcement(it) }
        return RemoteDialog.None
    }
}
