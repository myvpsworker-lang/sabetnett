package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RemoteDialogPickerTest {
    private val announcement = RemoteAnnouncement(1, "t", "x", null)
    private val emergency = RemoteEmergency(7, "e", "down", null)
    private val whatsNew = RemoteWhatsNew(10, "n", "new")
    private val full = RemoteConfig(
        announcement = announcement,
        emergency = emergency,
        whatsNew = whatsNew,
        features = RemoteFeatures(onboarding = true),
        onboarding = listOf(RemoteSlide("✨", "a", "b")),
    )

    @Test
    fun priorityIsOnboardingEmergencyWhatsNewAnnouncement() {
        var d = RemoteDismissed()
        assertEquals(RemoteDialog.Onboarding, RemoteDialogPicker.pick(full, d, 0, 10))
        d = d.copy(onboardingSeen = true)
        assertEquals(RemoteDialog.Emergency(emergency), RemoteDialogPicker.pick(full, d, 0, 10))
        assertEquals(RemoteDialog.WhatsNew(whatsNew), RemoteDialogPicker.pick(full, d, 7, 10))
        d = d.copy(whatsNewSeen = 10)
        assertEquals(RemoteDialog.Announcement(announcement), RemoteDialogPicker.pick(full, d, 7, 10))
        d = d.copy(announcementId = 1)
        assertEquals(RemoteDialog.None, RemoteDialogPicker.pick(full, d, 7, 10))
    }

    @Test
    fun whatsNewWaitsUntilTheUpdateIsInstalled() {
        val c = RemoteConfig(whatsNew = whatsNew)
        assertEquals(RemoteDialog.None, RemoteDialogPicker.pick(c, RemoteDismissed(), 0, 9))
        assertEquals(RemoteDialog.WhatsNew(whatsNew), RemoteDialogPicker.pick(c, RemoteDismissed(), 0, 10))
    }

    @Test
    fun onboardingNeedsSwitchAndSlides() {
        val off = full.copy(features = RemoteFeatures(onboarding = false), emergency = null, whatsNew = null, announcement = null)
        assertEquals(RemoteDialog.None, RemoteDialogPicker.pick(off, RemoteDismissed(), 0, 10))
    }
}
