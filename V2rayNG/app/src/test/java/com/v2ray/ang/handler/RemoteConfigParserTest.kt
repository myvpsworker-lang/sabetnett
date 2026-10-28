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
        assertNull(c.banner)
        assertNull(c.announcement)
        assertTrue(c.sponsors.isEmpty())
    }

    @Test
    fun parsesEnabledBannerAndAnnouncement() {
        val c = RemoteConfigParser.parse(
            """{"banner":{"enabled":true,"id":3,"text":" سلام ","url":"https://t.me/x","label":"برو"},
               "announcement":{"enabled":true,"id":2,"title":"T","text":"body","url":"tg://resolve?domain=a"}}"""
        )!!
        assertEquals(RemoteBanner(3, "سلام", "https://t.me/x", "برو"), c.banner)
        assertEquals(RemoteAnnouncement(2, "T", "body", "tg://resolve?domain=a"), c.announcement)
    }

    @Test
    fun disabledOrEmptyTextBlocksAreDropped() {
        val c = RemoteConfigParser.parse(
            """{"banner":{"enabled":false,"id":1,"text":"x"},"announcement":{"enabled":true,"id":1,"text":"  "}}"""
        )!!
        assertNull(c.banner)
        assertNull(c.announcement)
    }

    @Test
    fun unsafeLinksAreRemovedButTextKept() {
        val c = RemoteConfigParser.parse(
            """{"banner":{"enabled":true,"id":1,"text":"hi","url":"javascript:alert(1)"}}"""
        )!!
        assertNotNull(c.banner)
        assertNull(c.banner!!.url)
    }

    @Test
    fun wrongTypesDoNotBreakParsing() {
        val c = RemoteConfigParser.parse(
            """{"banner":"oops","announcement":5,"sponsors":{"a":1},"sponsorsPage":[1]}"""
        )!!
        assertNull(c.banner)
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
        assertEquals(400, c.banner!!.text.length)
    }
}
