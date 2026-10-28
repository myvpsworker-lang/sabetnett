package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateCheckerManagerTest {
    private val update = RemoteUpdate(10, "2.0", "https://a.b/app.apk", "notes")

    @Test
    fun newerVersionOffersUpdate() {
        val r = UpdateCheckerManager.resultFor(update, 9)
        assertTrue(r.hasUpdate)
        assertEquals("2.0", r.latestVersion)
        assertEquals("https://a.b/app.apk", r.downloadUrl)
        assertEquals("notes", r.releaseNotes)
    }

    @Test
    fun sameOrOlderVersionIsUpToDate() {
        assertFalse(UpdateCheckerManager.resultFor(update, 10).hasUpdate)
        assertFalse(UpdateCheckerManager.resultFor(update, 11).hasUpdate)
    }

    @Test
    fun noPublishedUpdateIsUpToDate() {
        assertFalse(UpdateCheckerManager.resultFor(null, 1).hasUpdate)
    }

    @Test
    fun blankVersionNameFallsBackToCode() {
        assertEquals("10", UpdateCheckerManager.resultFor(update.copy(versionName = ""), 1).latestVersion)
    }
}
