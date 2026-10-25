package cn.luorenmu.plugins.bililive

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class BiliLiveSourceTest {
    private val room = """{"code":0,"data":{"room_id":5440,"uid":42,"title":"直播标题","live_status":1,"live_time":"2026-10-23 08:00:00","user_cover":"http://i0.hdslb.com/bfs/live/cover.jpg","keyframe":"https://i1.hdslb.com/fallback.png"}}"""
    private val anchor = """{"code":0,"data":{"info":{"uname":"测试UP主"}}}"""

    @Test fun `source returns anchor name cover and live session with cached anchor lookup`() = runBlocking<Unit> {
        var anchors = 0
        var clock = 0L
        val source = BiliLiveSource(load = { uri ->
            if (uri.path.endsWith("get_info")) room else { anchors++; anchor }
        }, now = { clock })
        val first = source.fetch(1)
        assertEquals("测试UP主", first.upName)
        assertEquals("http://i0.hdslb.com/bfs/live/cover.jpg", first.coverUrl)
        assertEquals("2026-10-23 08:00:00", first.session)
        source.fetch(5440)
        assertEquals(1, anchors)
        clock = 3_600_001
        source.fetch(5440)
        assertEquals(2, anchors)
    }

    @Test fun `anchor metadata failure retains room state and cancellation propagates`() = runBlocking<Unit> {
        val source = BiliLiveSource(load = { uri -> if (uri.path.endsWith("get_info")) room else error("limited") })
        val result = source.fetch(1)
        assertEquals(1, result.status)
        assertEquals("", result.upName)
        val cancelled = BiliLiveSource(load = { uri -> if (uri.path.endsWith("get_info")) room else throw CancellationException("unload") })
        assertFailsWith<CancellationException> { cancelled.fetch(1) }
    }

    @Test fun `empty or unsafe cover falls back to keyframe`() {
        val result = BiliLiveSource().parse(room.replace("http://i0.hdslb.com/bfs/live/cover.jpg", "http://127.0.0.1/private"))
        assertEquals("https://i1.hdslb.com/fallback.png", result.coverUrl)
    }
}
