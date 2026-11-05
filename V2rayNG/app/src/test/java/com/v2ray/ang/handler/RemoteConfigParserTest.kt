package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemoteConfigParserTest {
    @Test
    fun invalidInputGivesNull() {
        assertNull(RemoteConfigParser.parse(null))
        assertNull(RemoteConfigParser.parse(""))
        assertNull(RemoteConfigParser.parse("not json"))
        assertNull(RemoteConfigParser.parse("[1,2]"))
        assertNull(RemoteConfigParser.parse("x".repeat(70_000)))
    }

    @Test
    fun emptyObjectGivesEmptyConfig() {
        val c = RemoteConfigParser.parse("{}")!!
        assertTrue(c.banners.isEmpty())
        assertNull(c.announcement)
        assertTrue(c.sponsors.isEmpty())
    }

    @Test
    fun parsesEnabledBannerAndAnnouncement() {
        val c = RemoteConfigParser.parse(
            """{"banner":{"enabled":true,"id":3,"text":" سلام ","url":"https://t.me/x","label":"برو"},
               "announcement":{"enabled":true,"id":2,"title":"T","text":"body","url":"tg://resolve?domain=a"}}"""
        )!!
        assertEquals(listOf(RemoteBanner(3, "سلام", "https://t.me/x", "برو")), c.banners)
        assertEquals(RemoteAnnouncement(2, "T", "body", "tg://resolve?domain=a"), c.announcement)
    }

    @Test
    fun disabledOrEmptyTextBlocksAreDropped() {
        val c = RemoteConfigParser.parse(
            """{"banner":{"enabled":false,"id":1,"text":"x"},"announcement":{"enabled":true,"id":1,"text":"  "}}"""
        )!!
        assertTrue(c.banners.isEmpty())
        assertNull(c.announcement)
    }

    @Test
    fun unsafeLinksAreRemovedButTextKept() {
        val c = RemoteConfigParser.parse(
            """{"banner":{"enabled":true,"id":1,"text":"hi","url":"javascript:alert(1)"}}"""
        )!!
        assertNotNull(c.banners.firstOrNull())
        assertNull(c.banners.first().url)
    }

    @Test
    fun wrongTypesDoNotBreakParsing() {
        val c = RemoteConfigParser.parse(
            """{"banner":"oops","announcement":5,"sponsors":{"a":1},"sponsorsPage":[1]}"""
        )!!
        assertTrue(c.banners.isEmpty())
        assertNull(c.announcement)
        assertTrue(c.sponsors.isEmpty())
    }

    @Test
    fun sponsorsAreFilteredAndCapped() {
        val items = (1..30).joinToString(",") { """{"title":"s$it","desc":"d","url":"https://a.b/$it"}""" }
        val c = RemoteConfigParser.parse("""{"sponsors":[{"title":""},"str",$items],"sponsorsPage":{"title":"حامیان","intro":"i"}}""")!!
        assertEquals(20, c.sponsors.size)
        assertEquals("s1", c.sponsors.first().title)
        assertEquals("حامیان", c.sponsorsTitle)
        assertEquals("i", c.sponsorsIntro)
    }

    @Test
    fun longTextIsTruncated() {
        val c = RemoteConfigParser.parse("""{"banner":{"enabled":true,"id":1,"text":"${"a".repeat(1000)}"}}""")!!
        assertEquals(400, c.banners.first().text.length)
    }

    @Test
    fun parsesFeaturesLinksSupportAndSlides() {
        val c = RemoteConfigParser.parse(
            """{"features":{"autoFastest":true,"onboarding":false},
               "support":{"label":"پشتیبانی","url":"https://t.me/s"},
               "links":{"channel":"https://t.me/c","privacy":"javascript:x","source":""},
               "onboarding":[{"title":"A","text":"a"},{"title":""},{"title":"B"}]}"""
        )!!
        assertTrue(c.features.autoFastest)
        assertEquals(false, c.features.onboarding)
        assertEquals(RemoteLinkItem("پشتیبانی", "https://t.me/s"), c.support)
        assertEquals("https://t.me/c", c.links.channel)
        assertNull(c.links.privacy)
        assertNull(c.links.source)
        assertEquals(listOf(RemoteSlide("✨", "A", "a"), RemoteSlide("✨", "B", "")), c.onboarding)
    }

    @Test
    fun missingFeaturesDefaultToOff() {
        val c = RemoteConfigParser.parse("{}")!!
        assertEquals(false, c.features.autoFastest)
        assertEquals(false, c.features.onboarding)
        assertNull(c.support)
        assertNull(c.defaultSub)
        assertNull(c.update)
    }

    @Test
    fun defaultSubNeedsEnabledAndHttpUrl() {
        assertNull(RemoteConfigParser.parse("""{"defaultSub":{"enabled":false,"url":"https://a.b/s"}}""")!!.defaultSub)
        assertNull(RemoteConfigParser.parse("""{"defaultSub":{"enabled":true,"url":"tg://x"}}""")!!.defaultSub)
        val d = RemoteConfigParser.parse("""{"defaultSub":{"enabled":true,"name":"","url":"https://a.b/s"}}""")!!.defaultSub
        assertEquals(RemoteDefaultSub("SabetNet", "https://a.b/s"), d)
    }

    @Test
    fun updateNeedsCodeAndSafeApkUrl() {
        assertNull(RemoteConfigParser.parse("""{"update":{"versionCode":0,"apkUrl":"https://a.b/x.apk"}}""")!!.update)
        assertNull(RemoteConfigParser.parse("""{"update":{"versionCode":5,"apkUrl":"file:///x"}}""")!!.update)
        val u = RemoteConfigParser.parse("""{"update":{"versionCode":5,"versionName":"1.2","apkUrl":"https://a.b/x.apk","changelog":"c"}}""")!!.update
        assertEquals(RemoteUpdate(5, "1.2", "https://a.b/x.apk", "c"), u)
    }

    @Test
    fun parsesRotatingBannersWithColorScheduleAndDismissFlag() {
        val c = RemoteConfigParser.parse(
            """{"banners":[
                 {"id":1,"text":"a","color":"#112233","dismissible":false,"start":100,"end":200},
                 {"id":2,"text":"b","color":"red"},
                 {"id":3,"text":""}]}"""
        )!!
        assertEquals(2, c.banners.size)
        val first = c.banners[0]
        assertEquals(0xFF112233.toInt(), first.colorArgb)
        assertEquals(false, first.dismissible)
        assertTrue(first.isActiveAt(150))
        assertEquals(false, first.isActiveAt(99))
        assertEquals(false, first.isActiveAt(200))
        assertNull(c.banners[1].colorArgb)
        assertTrue(c.banners[1].dismissible)
        assertTrue(c.banners[1].isActiveAt(Long.MAX_VALUE - 1))
    }

    @Test
    fun bannersAreCapped() {
        val items = (1..9).joinToString(",") { """{"id":$it,"text":"t$it"}""" }
        assertEquals(5, RemoteConfigParser.parse("""{"banners":[$items]}""")!!.banners.size)
    }

    @Test
    fun parseColorAcceptsOnlySixDigitHex() {
        assertEquals(0xFFFFFFFF.toInt(), RemoteConfigParser.parseColor("#ffffff"))
        assertNull(RemoteConfigParser.parseColor("#fff"))
        assertNull(RemoteConfigParser.parseColor("ffffff"))
        assertNull(RemoteConfigParser.parseColor("#gggggg"))
    }

    @Test
    fun parsesEmergencyWhatsNewAndSubscriptionLink() {
        val c = RemoteConfigParser.parse(
            """{"emergency":{"enabled":true,"id":4,"title":"E","text":"down"},
               "whatsNew":{"versionCode":12,"title":"New","text":"stuff"},
               "links":{"subscriptionTelegram":"https://t.me/me"}}"""
        )!!
        assertEquals(RemoteEmergency(4, "E", "down", null), c.emergency)
        assertEquals(RemoteWhatsNew(12, "New", "stuff"), c.whatsNew)
        assertEquals("https://t.me/me", c.links.subscriptionTelegram)
    }
}
