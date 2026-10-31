package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushMessageManagerTest {
    private val push = RemotePush(5, "t", "x", null)

    @Test
    fun noMessageNeverShows() = assertFalse(PushMessageManager.shouldShow(null, 0))

    @Test
    fun newMessageShowsOnceThenNot() {
        assertTrue(PushMessageManager.shouldShow(push, 0))
        assertTrue(PushMessageManager.shouldShow(push, 4))
        assertFalse(PushMessageManager.shouldShow(push, 5))
    }

    @Test
    fun resendWithNewIdShowsAgain() = assertTrue(PushMessageManager.shouldShow(push.copy(id = 6), 5))

    @Test
    fun parserBuildsPushOnlyWhenEnabledWithTextAndId() {
        fun parse(j: String) = RemoteConfigParser.parse(j)!!.push
        assertTrue(parse("""{"push":{"enabled":true,"id":2,"title":"T","text":"hello","url":"https://a.b"}}""") == RemotePush(2, "T", "hello", "https://a.b"))
        assertTrue(parse("""{"push":{"enabled":false,"id":2,"text":"hello"}}""") == null)
        assertTrue(parse("""{"push":{"enabled":true,"id":2,"text":""}}""") == null)
        assertTrue(parse("""{"push":{"enabled":true,"id":0,"text":"x"}}""") == null)
        assertTrue(parse("""{"push":{"enabled":true,"id":3,"text":"x","url":"javascript:1"}}""")!!.url == null)
    }

    @Test
    fun parserReadsAccentAndSmartReconnectFlag() {
        val c = RemoteConfigParser.parse("""{"theme":{"accent":"#059669"},"features":{"smartReconnect":true}}""")!!
        assertTrue(c.theme.accentArgb == 0xFF059669.toInt())
        assertTrue(c.features.smartReconnect)
        val d = RemoteConfigParser.parse("""{"theme":{"accent":"green"}}""")!!
        assertTrue(d.theme.accentArgb == null)
        assertFalse(d.features.smartReconnect)
    }
}
