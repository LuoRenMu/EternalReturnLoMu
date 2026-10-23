package cn.luorenmu.plugins.test

import cn.luorenmu.command.entity.MessageSender
import kotlinx.coroutines.*
import cn.luorenmu.plugins.qqmedia.QQGroupPermissionResult
import cn.luorenmu.plugins.qqmedia.QQGroupPermissionStatus
import love.forte.simbot.common.id.StringID.Companion.ID
import kotlin.test.*

class ProactiveTestCommandTest {
    @Test fun `test reports group permission separately from sender identity`() = runBlocking<Unit> {
        val scheduler = ProactiveTestScheduler()
        val sender = MessageSender("A".repeat(32).ID, "member", "B".repeat(32).ID, "/test", "/test")
        for ((role, expected) in listOf("owner" to "群主（群主或管理员：是）", "admin" to "管理员（群主或管理员：是）", null to "无法确认", "member" to "无法确认")) {
            sender.qqGroupMemberRole = role
            val command = ProactiveTestCommand(scheduler) { QQGroupPermissionResult(QQGroupPermissionStatus.ENABLED) }
            val reply = command.listen(sender, emptyMap()).toString()
            assertTrue(reply.contains("主动消息：已开启"))
            assertTrue(reply.contains("发送者身份：$expected"))
        }
    }

    @Test fun `denied missing disabled timeout and errors are displayed without suppressing test schedule`() = runBlocking<Unit> {
        val scheduler = ProactiveTestScheduler({ awaitCancellation() }, { error("test does not send real messages") })
        scheduler.start()
        try {
            val sender = MessageSender("A".repeat(32).ID, "member", "B".repeat(32).ID, "/test", "/test")
            for ((status, text) in listOf(QQGroupPermissionStatus.DISABLED to "未开启", QQGroupPermissionStatus.NO_API_PERMISSION to "无法确认（11253", QQGroupPermissionStatus.NOT_PROVIDED to "无法确认", QQGroupPermissionStatus.FAILED to "无法确认")) {
                val reply = ProactiveTestCommand(scheduler) { QQGroupPermissionResult(status) }.listen(sender, emptyMap()).toString()
                assertTrue(reply.contains("主动消息：$text"))
                assertTrue(reply.contains("5 秒"))
                assertTrue(reply.contains("发送者身份：无法确认"))
            }
            val timeout = ProactiveTestCommand(scheduler) { withTimeout(1) { delay(100); error("never") } }
            assertTrue(timeout.listen(sender, emptyMap()).toString().contains("无法确认（查询超时）"))
            val cancelled = ProactiveTestCommand(scheduler) { throw CancellationException("shutdown") }
            assertFailsWith<CancellationException> { cancelled.listen(sender, emptyMap()) }
        } finally { scheduler.stop() }
    }

    @Test fun `command schedules exactly one send in originating group after five second delay`() = runBlocking<Unit> {
        val delayRequested = CompletableDeferred<Long>()
        val release = CompletableDeferred<Unit>()
        val sent = CompletableDeferred<String>()
        val scheduler = ProactiveTestScheduler({ milliseconds -> delayRequested.complete(milliseconds); release.await() }, { sent.complete(it) })
        scheduler.start()
        try {
            val group = "A".repeat(32)
            val reply = ProactiveTestCommand(scheduler) { QQGroupPermissionResult(QQGroupPermissionStatus.ENABLED) }.listen(MessageSender(group.ID, "member", "B".repeat(32).ID, "/test", "/test"), emptyMap())
            assertTrue(reply.toString().contains("5 秒"))
            assertEquals(5000L, withTimeout(2000) { delayRequested.await() })
            assertFalse(sent.isCompleted)
            release.complete(Unit)
            assertEquals(group, withTimeout(2000) { sent.await() })
        } finally { scheduler.stop() }
    }

    @Test fun `unload cancels pending send and disabled scheduler rejects new work`() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val scheduler = ProactiveTestScheduler({
            entered.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        }, { error("must not send after unload") })
        assertFalse(scheduler.schedule("A".repeat(32)))
        scheduler.start()
        assertTrue(scheduler.schedule("A".repeat(32)))
        withTimeout(2000) { entered.await() }
        scheduler.stop()
        withTimeout(2000) { cancelled.await() }
        assertFalse(scheduler.schedule("A".repeat(32)))
    }

    @Test fun `non group command never schedules`() = runBlocking<Unit> {
        val scheduler = ProactiveTestScheduler({ error("must not wait") }, { error("must not send") })
        scheduler.start()
        try {
            val result = ProactiveTestCommand(scheduler).listen(MessageSender("web-admin-group".ID, "member", "user".ID, "/test", "/test"), emptyMap())
            assertTrue(result.toString().contains("QQ 群"))
            assertTrue(TestPlugin().commands.single() is ProactiveTestCommand)
        } finally { scheduler.stop() }
    }
}
