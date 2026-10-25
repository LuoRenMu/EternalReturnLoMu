package cn.luorenmu.plugins.bililive

import kotlinx.coroutines.*
import java.sql.DriverManager
import java.nio.file.Path
import org.junit.jupiter.api.io.TempDir
import kotlin.test.*

class BiliLiveMonitorTest {
    @TempDir lateinit var dir: Path
    private fun monitorForTest(
        repository: BiliLiveRepository,
        fetch: suspend (Long) -> BiliRoom,
        send: suspend (String, BiliLiveNotice) -> Unit,
        pause: suspend (Long) -> Unit = {},
        now: () -> Long = System::currentTimeMillis,
        poll: suspend (Long) -> BiliRoom = fetch,
        permission: suspend (String) -> Boolean = { true },
    ) = BiliLiveMonitor(repository, fetch, send, permission, pause, now, poll, awaitPoll = { awaitCancellation() })
    private fun user(group: String) = group.padEnd(32, '0')
    private fun repository() = BiliLiveRepository { block ->
        DriverManager.getConnection("jdbc:sqlite:${dir.resolve("live.db")}").use(block)
    }.apply { claim(LIVE_CLAIM_GROUP, user("A")); claim(LIVE_CLAIM_GROUP, user("B")) }
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
        repo.add("A", user("A"),room(0,"")); repo.add("B", user("B"),room(0,""))
        val sends = mutableListOf<String>()
        var fetches = 0
        var current = room(1)
        fun monitor() = monitorForTest(repository(), { fetches++; current }, { group,_ -> sends += group }, {})
        monitor().tick()
        assertEquals(1,fetches); assertEquals(listOf("A","B"),sends)
        assertEquals(2L, repo.pushCount(5440))
        assertEquals(1L, repo.pushCount(5440, "A"))
        assertEquals(1L, repository().pushCount(5440, "B"), "counts survive reload independently of the live cursor")
        monitor().tick(); assertEquals(2,sends.size)
        current = room(2); monitor().tick()
        current = room(1); monitor().tick() // same session after a temporary status flap
        assertEquals(4,sends.size, "each group receives one end notice, with no duplicate start for the same session")
        current = room(2); monitor().tick()
        assertEquals(4,sends.size, "a status flap cannot create another end notice for the same session")
        current = room(1,"session-2"); monitor().tick()
        assertEquals(6,sends.size)
        assertEquals(6L, repo.pushCount(5440))
        assertTrue(repo.remove("A", 5440))
        assertTrue(repo.remove("B", 5440))
        assertEquals(6L, repository().pushCount(5440), "cancelling subscriptions must not delete cumulative statistics")
    }

    @Test fun `subscription canonicalizes short ID and replays current live once after restart`() = runBlocking<Unit> {
        val repo = repository()
        var sends = 0
        val service = monitorForTest(repo,{ room(1) },{ _,_ -> sends++ },{})
        assertContains(service.command("A", user("A"),"订阅","1"),"已监听直播间 5440")
        assertEquals(0L, repo.pushCount(), "subscription acknowledgement is not an active live notification")
        assertContains(service.command("A", user("A"),"订阅","5440"),"已监听")
        assertEquals(1,repo.rows().size)
        monitorForTest(repository(),{ room(1) },{ _,_ -> sends++ },{}).tick()
        service.tick(); assertEquals(1,sends)
        assertEquals(1L, repo.pushCount(5440), "first live replay counts once and unchanged polling never counts")
        assertContains(service.command("A", user("A"),"列表",""),"5440")
        assertContains(service.command("A", user("A"),"取消","5440"),"已取消")
        service.tick(); assertEquals(1,sends)
    }

    @Test fun `enabled monitor immediately replays a live subscription without waiting for any background poll`() = runBlocking<Unit> {
        val repo = repository()
        val sent = CompletableDeferred<Pair<String, BiliLiveNotice>>()
        var manualFetches = 0
        var sends = 0
        val monitor = monitorForTest(repo, { manualFetches++; room(1) }, { group, notice ->
            sends++
            sent.complete(group to notice)
        }, poll = { error("initial replay must not wait for or fetch through the background queue") })
        monitor.start()
        try {
            assertContains(monitor.command("A", user("A"), "订阅", "1"), "当前直播中")
            val (group, notice) = withTimeout(2_000) { sent.await() }
            assertEquals("A", group)
            assertEquals(BiliLiveEvent.STARTED, notice.event)
            assertEquals("session-1", notice.room.session)
            assertContains(monitor.command("A", user("A"), "订阅", "5440"), "已监听")
            assertContains(monitor.command("A", user("A"), "订阅", "1"), "已监听")
            assertEquals(1, sends, "canonical and short-ID duplicates must not replay again")
            assertEquals(1L, repo.pushCount())
            assertFalse(repo.rows().single().session.startsWith(INITIAL_LIVE_PREFIX))
            assertEquals(2, manualFetches, "reuse the snapshot fetched by the successful subscription")
        } finally { monitor.stop() }
    }

    @Test fun `initial replay is asynchronous and successful subscriptions in separate groups each notify once`() = runBlocking<Unit> {
        val repo = repository()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val sent = mutableListOf<String>()
        val monitor = monitorForTest(repo, { room(1) }, { group, _ ->
            if (group == "A") { started.complete(Unit); release.await() }
            sent += group
        })
        monitor.start()
        try {
            // The command must finish even while the asynchronous active send remains blocked.
            assertContains(withTimeout(2_000) { monitor.command("A", user("A"), "订阅", "1") }, "已监听")
            withTimeout(2_000) { started.await() }
            release.complete(Unit)
            assertContains(monitor.command("B", user("A"), "订阅", "1"), "已监听")
            withTimeout(2_000) {
                while (repo.pushCount() != 2L) delay(10)
            }
            monitor.tick(); monitor.tick()
            assertEquals(listOf("A", "B"), sent)
            assertEquals(1L, repo.pushCount(group = "A"))
            assertEquals(1L, repo.pushCount(group = "B"))
        } finally { release.complete(Unit); monitor.stop() }
    }

    @Test fun `denied initial replay retains pending cursor for a later permitted poll`() = runBlocking<Unit> {
        val repo = repository()
        val checked = CompletableDeferred<Unit>()
        var enabled = false
        var sends = 0
        val monitor = monitorForTest(repo, { room(1) }, { _, _ -> sends++ }, permission = {
            checked.complete(Unit)
            enabled
        })
        monitor.start()
        try {
            monitor.command("A", user("A"), "订阅", "1")
            withTimeout(2_000) { checked.await() }
            monitor.command("A", user("A"), "列表", "") // await the permission decision under the same lock
            assertEquals(0, sends)
            assertTrue(repo.rows().single().session.startsWith(INITIAL_LIVE_PREFIX))
            enabled = true
            monitor.tick(); monitor.tick()
            assertEquals(1, sends)
            assertEquals(1L, repo.pushCount())
        } finally { monitor.stop() }
    }

    @Test fun `initial replays and regular live events share the same send spacing`() = runBlocking<Unit> {
        val repo = repository()
        val time = java.util.concurrent.atomic.AtomicLong(0)
        val current = java.util.concurrent.atomic.AtomicReference(room(1))
        val sentAt = mutableListOf<Long>()
        val monitor = monitorForTest(repo, { current.get() }, { _, _ -> sentAt += time.get() },
            pause = { time.addAndGet(it) }, now = time::get)
        monitor.start()
        try {
            monitor.command("A", user("A"), "订阅", "1")
            monitor.command("B", user("A"), "订阅", "1")
            withTimeout(2_000) { while (repo.pushCount() != 2L) delay(10) }
            current.set(room(0, ""))
            monitor.tick()
            assertEquals(listOf(0L, 6_100L, 12_200L, 18_300L), sentAt)
            assertEquals(4L, repo.pushCount())
        } finally { monitor.stop() }
    }

    @Test fun `failed immediate replay is not repeated by polling reload or a duplicate subscription`() = runBlocking<Unit> {
        val repo = repository()
        val attempted = CompletableDeferred<Unit>()
        var attempts = 0
        val send: suspend (String, BiliLiveNotice) -> Unit = { _, _ ->
            attempts++
            attempted.complete(Unit)
            error("accepted but response lost")
        }
        val monitor = monitorForTest(repo, { room(1) }, send)
        monitor.start()
        try {
            monitor.command("A", user("A"), "订阅", "1")
            withTimeout(2_000) { attempted.await() }
            monitor.command("A", user("A"), "订阅", "5440")
            monitor.tick()
            monitorForTest(repository(), { room(1) }, send).tick()
            assertEquals(1, attempts)
            assertEquals(0L, repo.pushCount())
            assertFalse(repo.rows().single().session.startsWith(INITIAL_LIVE_PREFIX))
        } finally { monitor.stop() }
    }

    @Test fun `unloading before immediate replay is claimed preserves it for the next monitor`() = runBlocking<Unit> {
        val repo = repository()
        val checking = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        var sends = 0
        val monitor = monitorForTest(repo, { room(1) }, { _, _ -> sends++ }, permission = {
            checking.complete(Unit)
            try { awaitCancellation() } finally { stopped.complete(Unit) }
        })
        monitor.start()
        try {
            monitor.command("A", user("A"), "订阅", "1")
            withTimeout(2_000) { checking.await() }
        } finally { monitor.stop() }
        withTimeout(2_000) { stopped.await() }
        assertTrue(repo.rows().single().session.startsWith(INITIAL_LIVE_PREFIX))
        val reloaded = monitorForTest(repository(), { room(1) }, { _, _ -> sends++ })
        reloaded.tick(); reloaded.tick()
        assertEquals(1, sends)
        assertEquals(1L, repo.pushCount())
    }

    @Test fun `subscribing to an offline or rerun room never queues an immediate live message`() = runBlocking<Unit> {
        val monitor = monitorForTest(repository(), { id -> BiliRoom(id, "title", if (id == 1L) 0 else 2, "") },
            { _, _ -> error("not live") }, permission = { error("no initial permission query should be scheduled") })
        monitor.start()
        try {
            assertContains(monitor.command("A", user("A"), "订阅", "1"), "当前未开播")
            assertContains(monitor.command("B", user("B"), "订阅", "2"), "当前未开播")
            assertEquals(2, repository().rows().size)
            assertTrue(repository().rows().none { it.session.startsWith(INITIAL_LIVE_PREFIX) })
        } finally { monitor.stop() }
    }

    @Test fun `first replay also works without session and never treats a rerun as live`() = runBlocking<Unit> {
        var current = room(1, "")
        val sent = mutableListOf<BiliLiveNotice>()
        val monitor = monitorForTest(repository(), { current }, { _, notice -> sent += notice }, {}, { 100L })
        assertContains(monitor.command("A", user("A"), "订阅", "1"), "当前直播中")
        monitor.tick(); monitor.tick()
        assertEquals(1, sent.size)
        assertEquals(100L, sent.single().timestamp)
        current = room(2, "")
        monitor.command("B", user("B"), "订阅", "1")
        monitor.tick()
        assertEquals(2, sent.size, "the live subscriber receives an end notice; the newly subscribed rerun room does not")
        assertEquals(BiliLiveEvent.ENDED, sent.last().event)
    }

    @Test fun `offline notification preserves known start after upstream clears it and is durable across reloads`() = runBlocking<Unit> {
        val repo = repository()
        repo.add("A", user("A"), room(0, ""))
        val started = "2026-10-23 06:35:00"
        var current = room(1, started)
        val sent = mutableListOf<BiliLiveNotice>()
        fun monitor() = monitorForTest(repository(), { current }, { _, notice -> sent += notice },
            now = { java.time.Instant.parse("2026-10-23T00:00:00Z").toEpochMilli() })
        monitor().tick()
        current = room(1, "") // A transient empty field must not destroy the known real start time.
        monitor().tick()
        assertEquals(started, repo.rows().single().session)
        current = room(0, "0000-00-00 00:00:00")
        monitor().tick(); monitor().tick()
        assertEquals(listOf(BiliLiveEvent.STARTED, BiliLiveEvent.ENDED), sent.map { it.event })
        assertEquals(started, sent.last().room.session)
        assertContains(sent.last().text, "直播时长：1小时 25分钟")
        assertEquals(2L, repo.pushCount(5440, "A"))
        current = room(2, "")
        monitor().tick()
        assertEquals(2, sent.size, "offline -> rerun is not a second end event")
    }

    @Test fun `cold subscriptions stay silent while unknown start sessions can end once per observed cycle`() = runBlocking<Unit> {
        val repo = repository()
        var current = room(0, "")
        val sent = mutableListOf<BiliLiveNotice>()
        fun monitor() = monitorForTest(repo, { current }, { _, notice -> sent += notice })
        monitor().command("A", user("A"), "订阅", "1")
        monitor().tick()
        current = room(2, "")
        monitor().tick()
        assertTrue(sent.isEmpty(), "never announce an end without previously observing a live room")
        repeat(2) {
            current = room(1, "")
            monitor().tick()
            current = room(0, "")
            monitor().tick(); monitor().tick()
        }
        assertEquals(listOf(BiliLiveEvent.STARTED, BiliLiveEvent.ENDED, BiliLiveEvent.STARTED, BiliLiveEvent.ENDED), sent.map { it.event })
        assertTrue(sent.filter { it.event == BiliLiveEvent.ENDED }.all { it.text.contains("直播时长：未知") })
        assertEquals(4L, repo.pushCount())
    }

    @Test fun `pending live replay can end before first poll without exposing the pending prefix`() = runBlocking<Unit> {
        val repo = repository()
        var current = room(1, "2026-10-23 06:35:00")
        val sent = mutableListOf<BiliLiveNotice>()
        val monitor = monitorForTest(repo, { current }, { _, notice -> sent += notice })
        monitor.command("A", user("A"), "订阅", "1")
        current = room(0, "")
        monitor.tick(); monitor.tick()
        assertEquals(BiliLiveEvent.ENDED, sent.single().event)
        assertEquals("2026-10-23 06:35:00", sent.single().room.session)
        assertFalse(repo.rows().single().session.startsWith(INITIAL_LIVE_PREFIX))
    }

    @Test fun `failed end send is not retried after restart or status flap and never increments success count`() = runBlocking<Unit> {
        val repo = repository()
        repo.add("A", user("A"), room(0, ""))
        var current = room(1)
        var ends = 0
        fun monitor() = monitorForTest(repository(), { current }, { _, notice ->
            if (notice.event == BiliLiveEvent.ENDED) { ends++; error("response lost") }
        })
        monitor().tick()
        current = room(0, "")
        monitor().tick(); monitor().tick()
        current = room(1)
        monitor().tick()
        current = room(2, "")
        monitor().tick()
        assertEquals(1, ends)
        assertEquals(1L, repo.pushCount(), "only the successful start notice counts")
    }

    @Test fun `end notifications respect permission pauses without consuming the offline transition`() = runBlocking<Unit> {
        val repo = repository()
        repo.add("A", user("A"), room(0, ""))
        var current = room(1)
        var enabled = true
        var fetches = 0
        val sent = mutableListOf<BiliLiveNotice>()
        fun monitor() = monitorForTest(repo, { fetches++; current }, { _, notice -> sent += notice }, permission = { enabled })
        monitor().tick()
        enabled = false
        current = room(0, "")
        monitor().tick()
        assertEquals(1, fetches)
        assertEquals(1, repo.rows().single().status)
        enabled = true
        monitor().tick(); monitor().tick()
        assertEquals(listOf(BiliLiveEvent.STARTED, BiliLiveEvent.ENDED), sent.map { it.event })
    }

    @Test fun `pending first replay records claim before ambiguous send and is not retried`() = runBlocking<Unit> {
        var attempts = 0
        fun monitor() = monitorForTest(repository(), { room(1) }, { _,_ -> attempts++; error("accepted but response lost") }, {})
        monitor().command("A", user("A"), "订阅", "1")
        monitor().tick(); monitor().tick()
        assertEquals(1, attempts)
        assertEquals(0L, repository().pushCount(), "a failed or ambiguous send is not an acknowledged success")
    }

    @Test fun `background uses staggered fetch but manual subscription bypasses the background queue`() = runBlocking<Unit> {
        var manual = 0
        var polled = 0
        val monitor = monitorForTest(repository(), { manual++; room(0, "") }, { _,_ -> }, {},
            poll = { polled++; room(0, "") })
        monitor.command("A", user("A"), "订阅", "1")
        monitor.tick()
        assertEquals(1, manual)
        assertEquals(1, polled)
    }

    @Test fun `failed fetch retains state and backs off while other rooms proceed`() = runBlocking<Unit> {
        val repo = repository()
        repo.add("A", user("A"),room(0,"")); repo.add("B", user("B"),BiliRoom(6000,"other",0,""))
        var time = 0L
        var failures = 0
        val sends = mutableListOf<String>()
        val monitor = monitorForTest(repo,{ id ->
            if (id == 5440L) { failures++; error("limited") }
            BiliRoom(id,"other",1,"new")
        },{ group,_ -> sends += group },{}, { time })
        monitor.tick(); monitor.tick()
        assertEquals(1,failures); assertEquals(listOf("B"),sends)
        assertEquals(0,repo.rows().first { it.room == 5440L }.status)
        time = 120_001; monitor.tick(); assertEquals(2,failures)
    }

    @Test fun `ambiguous send failure is not repeated and cancellation propagates`() = runBlocking<Unit> {
        val repo = repository(); repo.add("A", user("A"),room(0,""))
        var sends = 0
        val monitor = monitorForTest(repo,{ room(1) },{ _,_ -> sends++; error("timeout after accepted") },{})
        monitor.tick(); monitor.tick(); assertEquals(1,sends)
        val cancelled = monitorForTest(repo,{ throw CancellationException("unload") },{ _,_ -> error("never") },{})
        assertFailsWith<CancellationException> { cancelled.tick() }
    }

    @Test fun `counter failure does not cause an acknowledged live message to be sent again`() = runBlocking<Unit> {
        val repo = repository()
        repo.add("A", user("A"), room(0, ""))
        assertEquals(0L, repo.pushCount())
        DriverManager.getConnection("jdbc:sqlite:${dir.resolve("live.db")}").use { c -> c.createStatement().use {
            it.execute("CREATE TRIGGER fail_push_count BEFORE INSERT ON qq_push_receipts BEGIN SELECT RAISE(ABORT,'fixture counter outage'); END")
        } }
        var sends = 0
        val monitor = monitorForTest(repo, { room(1) }, { _,_ -> sends++ })
        monitor.tick(); monitor.tick()
        assertEquals(1, sends)
        assertEquals(0L, repo.pushCount(), "an unavailable statistics database is logged, not fabricated")
        assertEquals("session-1", repo.rows().single().session, "bookkeeping failure must not reset the delivery cursor")
    }

    @Test fun `compare and set rejects stale cursor and cancellation cannot recreate row`() {
        val repo = repository(); repo.add("A", user("A"),room(0,""))
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
        val monitor = monitorForTest(repo,{ id -> fetches++; BiliRoom(id,"test",0,"") },{ _,_ -> },{})
        assertContains(monitor.command("A", user("A"),"订阅","bad"),"用法")
        assertEquals(0,fetches)
        monitor.command("A", user("A"),"订阅","1")
        assertContains(monitor.command("A", user("A"),"订阅","4"),"每群只允许")
        assertContains(monitor.command("B", user("B"),"订阅","4"),"已监听")
        assertEquals(2,repo.rows().size)
    }

    @Test fun `same sender can share a canonical room across groups but cannot subscribe to another room`() = runBlocking<Unit> {
        val repo = repository()
        val monitor = monitorForTest(repo, { id -> BiliRoom(if (id == 1L) 5440 else 6000, "title", 0, "") }, { _,_ -> })
        assertContains(monitor.command("A", user("A"), "订阅", "1"), "已监听")
        assertContains(monitor.command("B", user("A"), "订阅", "1"), "已监听")
        assertContains(monitor.command("C", user("A"), "订阅", "2"), "每位用户只允许")
        assertContains(monitor.command("B", user("A"), "订阅", "2"), "每群只允许")
        monitor.command("A", user("A"), "取消", "5440")
        assertContains(monitor.command("C", user("A"), "订阅", "2"), "每位用户只允许")
        monitor.command("B", user("A"), "取消", "5440")
        assertContains(monitor.command("C", user("A"), "订阅", "2"), "已监听")
        assertEquals(6000L, repo.activeRows().single().room)
    }

    @Test fun `disabled groups do not query or consume cursor and resume after permission is enabled`() = runBlocking<Unit> {
        val repo = repository()
        repo.add("A", user("A"),room(0,""))
        var enabled = false
        var fetches = 0
        var sends = 0
        val monitor = monitorForTest(repo, { fetches++; room(1) }, { _,_ -> sends++ }, permission = { enabled })
        monitor.tick()
        assertEquals(0, fetches)
        assertEquals(0L, repo.pushCount())
        assertEquals(0, repo.rows().single().status)
        enabled = true
        monitor.tick(); monitor.tick()
        assertEquals(1, sends)
        assertEquals(1L, repo.pushCount())
        assertEquals(1, repo.rows().single().status)
    }

    @Test fun `shared room fetch fans out only to permitted groups without consuming disabled cursors`() = runBlocking<Unit> {
        val repo = repository()
        repo.add("A", user("A"),room(0,"")); repo.add("B", user("B"),room(0,""))
        var allowB = false
        var fetches = 0
        val sent = mutableListOf<String>()
        val monitor = monitorForTest(repo, { fetches++; room(1) }, { group,_ -> sent += group }, permission = { it == "A" || allowB })
        monitor.tick()
        assertEquals(1, fetches)
        assertEquals(listOf("A"), sent)
        assertEquals(0, repo.rows().single { it.group == "B" }.status)
        allowB = true
        monitor.tick()
        assertEquals(listOf("A", "B"), sent)
    }

}
