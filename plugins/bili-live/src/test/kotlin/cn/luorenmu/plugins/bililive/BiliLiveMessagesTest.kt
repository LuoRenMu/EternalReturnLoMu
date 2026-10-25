package cn.luorenmu.plugins.bililive

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import love.forte.simbot.message.*
import java.time.Instant
import java.util.Base64
import kotlin.test.*

class BiliLiveMessagesTest {
    private val png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a0XcAAAAASUVORK5CYII=")
    private val notice = BiliLiveNotice(BiliRoom(5440, "直播标题 https://example.com/test", 1, "2026-10-23 06:35:00",
        "测试UP主", "http://i0.hdslb.com/bfs/live/cover.png"), Instant.parse("2026-10-23T00:00:00Z").toEpochMilli())

    @Test fun `notification combines offline cover bytes and metadata without any HTTP link`() = runBlocking<Unit> {
        var downloads = 0
        val builder = BiliLiveMessages(fetchCover = { uri -> assertEquals("https", uri.scheme); downloads++; png })
        val elements = assertIs<Messages>(builder.build(notice)).toList()
        val text = elements.filterIsInstance<PlainText>().joinToString { it.text }
        assertContains(text, "UP主：测试UP主")
        assertContains(text, "直播标题：直播标题")
        assertTrue(text.startsWith("已开播！"))
        assertContains(text, "已开播：1小时 25分钟")
        assertContains(text, "直播间：5440")
        assertFalse(text.contains("当前时间"))
        assertIs<OfflineImage>(elements.first())
        assertFalse(text.contains("http", ignoreCase = true))
        assertContentEquals(png, elements.filterIsInstance<OfflineImage>().single().data())
        builder.build(notice)
        assertEquals(1, downloads, "one cover download fans out to multiple groups")
    }

    @Test fun `missing invalid or failed cover falls back to link free text and cancellation escapes`() = runBlocking<Unit> {
        val failed = BiliLiveMessages(fetchCover = { error("HTTP failed") })
        assertContains(assertIs<PlainText>(failed.build(notice)).text, "封面暂不可用")
        val invalid = BiliLiveMessages(fetchCover = { "not an image at all".toByteArray() })
        assertIs<PlainText>(invalid.build(notice))
        val cancelled = BiliLiveMessages(fetchCover = { throw CancellationException("unload") })
        assertFailsWith<CancellationException> { cancelled.build(notice) }
    }

    @Test fun `ended notice sends cover first and exact requested caption with saved live duration`() = runBlocking<Unit> {
        val ended = notice.copy(room = notice.room.copy(status = 0), event = BiliLiveEvent.ENDED)
        val elements = assertIs<Messages>(BiliLiveMessages(fetchCover = { png }).build(ended)).toList()
        assertIs<OfflineImage>(elements.first())
        assertContentEquals(png, elements.filterIsInstance<OfflineImage>().single().data())
        assertEquals("已下播喵~\nUP主：测试UP主\n直播标题：直播标题\n直播时长：1小时 25分钟\n直播间：5440",
            elements.filterIsInstance<PlainText>().single().text)
        assertContains(ended.copy(room = ended.room.copy(session = "")).text, "直播时长：未知")
        assertFalse(ended.text.contains("已开播"))
    }

    @Test fun `cover URLs reject arbitrary hosts credentials ports and redirect targets`() {
        for (url in listOf("file:///tmp/secret", "http://127.0.0.1/a", "https://hdslb.com.evil/a", "https://x.hdslb.com:8080/a", "https://user:pass@i0.hdslb.com/a", "https://i0.hdslb.com/a#fragment"))
            assertNull(biliCoverUri(url))
        assertEquals("https://i0.hdslb.com/a", biliCoverUri("http://i0.hdslb.com/a").toString())
    }

    @Test fun `live duration handles minutes hours offsets invalid and future starts without inventing data`() {
        val now = Instant.parse("2026-10-23T00:00:00Z").toEpochMilli()
        assertEquals("0分钟", liveElapsed("2026-10-23 08:00:00", now))
        assertEquals("9分钟", liveElapsed("2026-10-23 07:50:30", now))
        assertEquals("2小时 3分钟", liveElapsed("2026-10-22T21:57:00Z", now))
        assertEquals("1小时 0分钟", liveElapsed("2026-10-23T08:00:00+09:00", now))
        assertEquals("未知", liveElapsed("0000-00-00 00:00:00", now))
        assertEquals("未知", liveElapsed("", now))
        assertEquals("未知", liveElapsed("2026-10-23 09:00:00", now))
    }
}
