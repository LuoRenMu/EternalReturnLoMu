package cn.luorenmu.plugins.test

import kotlinx.coroutines.*
import java.sql.DriverManager
import java.nio.file.Path
import org.junit.jupiter.api.io.TempDir
import cn.luorenmu.command.entity.MessageSender
import love.forte.simbot.common.id.StringID.Companion.ID
import kotlin.test.*

class BiliLiveMonitorTest {
    @TempDir lateinit var dir: Path
    private fun repository() = BiliLiveRepository { block ->
        DriverManager.getConnection("jdbc:sqlite:${dir.resolve("live.db")}").use(block)
    }
    private fun room(status: Int, session: String = "session-1") = BiliRoom(5440,"测试直播",status,session)

    @Test fun `room input accepts numeric and official links only`() {
        assertEquals(1L,biliRoomId("1"))
        assertEquals(1L,biliRoomId("https://live.bilibili.com/blanc/1?from=test"))
        for (input in listOf("0","-1","https://evil.com/1","https://live.bilibili.com.evil/1","file:///1","999999999999999999999"))
            assertNull(biliRoomId(input))
    }

    @Test fun `API validates envelope and all three statuses`() {
        val source = BiliLiveSource()
        for (status in 0..2) {
            val result = source.parse("""{"code":0,"data":{"room_id":5440,"title":"测试","live_status":$status,"live_time":"2026-10-23 10:00:00"}}""")
            assertEquals(status,result.status)
            assertEquals(5440L,result.id)
        }
        assertFails { source.parse("""{"code":1,"data":null}""") }
        assertFails { source.parse("""{"code":0,"data":{"room_id":5440,"live_status":8}}""") }
    }

    @Test fun `one fetch per room fans out and persisted session survives reload without duplicate`() = runBlocking<Unit> {
        val repo = repository()
        repo.add("A",room(0,"")); repo.add("B",room(0,""))
        val sends = mutableListOf<String>()
        var fetches = 0
        var current = room(1)
        fun monitor() = BiliLiveMonitor(repository(), { fetches++; current }, { group,_ -> sends += group }, {})
        monitor().tick()
        assertEquals(1,fetches); assertEquals(listOf("A","B"),sends)
        monitor().tick(); assertEquals(2,sends.size)
        current = room(2); monitor().tick()
        current = room(1); monitor().tick() // same session after a temporary status flap
        assertEquals(2,sends.size)
        current = room(1,"session-2"); monitor().tick()
        assertEquals(4,sends.size)
    }

    @Test fun `subscription canonicalizes short room ID and is silent for already live room`() = runBlocking<Unit> {
        val repo = repository()
        var sends = 0
        val service = BiliLiveMonitor(repo,{ room(1) },{ _,_ -> sends++ },{})
        assertContains(service.command("A","1",""),"已监听直播间 5440")
        assertContains(service.command("A","5440",""),"已监听")
        assertEquals(1,repo.rows().size)
        service.tick(); assertEquals(0,sends)
        assertContains(service.command("A","列表",""),"5440")
        assertContains(service.command("A","取消","5440"),"已取消")
        service.tick(); assertEquals(0,sends)
    }

    @Test fun `failed fetch retains state and backs off while other rooms proceed`() = runBlocking<Unit> {
        val repo = repository()
        repo.add("A",room(0,"")); repo.add("B",BiliRoom(6000,"other",0,""))
        var time = 0L
        var failures = 0
        val sends = mutableListOf<String>()
        val monitor = BiliLiveMonitor(repo,{ id ->
            if (id == 5440L) { failures++; error("limited") }
            BiliRoom(id,"other",1,"new")
        },{ group,_ -> sends += group },{}, { time })
        monitor.tick(); monitor.tick()
        assertEquals(1,failures); assertEquals(listOf("B"),sends)
        assertEquals(0,repo.rows().first { it.room == 5440L }.status)
        time = 120_001; monitor.tick(); assertEquals(2,failures)
    }

    @Test fun `ambiguous send failure is not repeated and cancellation propagates`() = runBlocking<Unit> {
        val repo = repository(); repo.add("A",room(0,""))
        var sends = 0
        val monitor = BiliLiveMonitor(repo,{ room(1) },{ _,_ -> sends++; error("timeout after accepted") },{})
        monitor.tick(); monitor.tick(); assertEquals(1,sends)
        val cancelled = BiliLiveMonitor(repo,{ throw CancellationException("unload") },{ _,_ -> error("never") },{})
        assertFailsWith<CancellationException> { cancelled.tick() }
    }

    @Test fun `compare and set rejects stale cursor and cancellation cannot recreate row`() {
        val repo = repository(); repo.add("A",room(0,""))
        val old = repo.rows().single()
        assertTrue(repo.advance(old,room(1)))
        assertFalse(repo.advance(old,room(1)))
        repo.remove("A",5440)
        assertFalse(repo.advance(old,room(1,"new")))
        assertTrue(repo.rows().isEmpty())
    }

    @Test fun `invalid input never fetches and group cap is independent`() = runBlocking<Unit> {
        val repo = repository()
        var fetches = 0
        val monitor = BiliLiveMonitor(repo,{ id -> fetches++; BiliRoom(id,"test",0,"") },{ _,_ -> },{})
        assertContains(monitor.command("A","bad",""),"用法")
        assertEquals(0,fetches)
        for (id in 1..3) monitor.command("A",id.toString(),"")
        assertContains(monitor.command("A","4",""),"最多")
        assertContains(monitor.command("B","4",""),"已监听")
        assertEquals(4,repo.rows().size)
    }

    @Test fun `live subcommand routes without permission probe or delayed test send`() = runBlocking<Unit> {
        val monitor = BiliLiveMonitor(repository(),{ room(0,"") },{ _,_ -> error("no send") },{})
        val command = ProactiveTestCommand(ProactiveTestScheduler(), live = { monitor }, permission = { error("no probe") })
        val sender = MessageSender("A".repeat(32).ID,"tester","B".repeat(32).ID,"/test 直播 1","/test 直播 1")
        assertContains(command.listen(sender,mapOf("action" to "直播", "room" to "1")).toString(),"已监听直播间 5440")
        assertContains(command.listen(sender,mapOf("action" to "直播", "room" to "列表")).toString(),"5440")
        assertContains(command.listen(sender,mapOf("action" to "直播", "room" to "取消", "target" to "5440")).toString(),"已取消")
        assertContains(command.listen(sender,mapOf("action" to "直播")).toString(),"用法")
    }
}
