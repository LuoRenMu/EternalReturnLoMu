package cn.luorenmu.plugins.grouppet

import cn.luorenmu.plugins.qqmedia.QQGroupPermissionResult
import cn.luorenmu.plugins.qqmedia.QQGroupPermissionStatus
import kotlinx.coroutines.*
import love.forte.simbot.message.toText
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import kotlin.test.*

internal fun grantedPermission() = QQGroupPermissionResult(QQGroupPermissionStatus.ENABLED)

class GroupPetPermissionTest {
    @TempDir lateinit var root: Path
    private val clock = MutableClock()
    private val group = "A".repeat(32)
    private val other = "B".repeat(32)
    private fun snapshot(id: Long = 100) = PetSnapshot(7, "백수", PetMatch(id, clock.millis() - 1000, 41))

    @Test fun `subscription requires explicit live enable before sync and database write`() = runBlocking<Unit> {
        val repository = GroupPetRepository(root.resolve("subscriptions.db"))
        repository.updatePermission(group, true, 1) // Local historical allow must never override a live denial.
        var lookups = 0
        var queries = 0
        var status = QQGroupPermissionStatus.DISABLED
        val service = GroupPetService(repository, { queries++; snapshot() }, { error("no render") }, { _, _ -> error("no send") }, clock,
            permission = { lookups++; QQGroupPermissionResult(status) })
        assertTrue(service.subscribe(group, "member", "bad name!").contains("不合法"))
        assertEquals(0, lookups)
        for (denied in listOf(QQGroupPermissionStatus.DISABLED, QQGroupPermissionStatus.NO_API_PERMISSION,
            QQGroupPermissionStatus.NOT_PROVIDED, QQGroupPermissionStatus.FAILED, QQGroupPermissionStatus.TIMEOUT)) {
            status = denied
            val message = service.subscribe(group, "member", "백수")
            assertTrue(message.contains("该功能需要群主在机器人页面设置"))
            if (denied == QQGroupPermissionStatus.NO_API_PERMISSION) assertTrue(message.contains("11253"))
            if (denied != QQGroupPermissionStatus.DISABLED) assertFalse(message.contains("本群未开启"))
            assertTrue(repository.list(group).isEmpty())
            assertEquals(0, queries)
        }
        status = QQGroupPermissionStatus.ENABLED
        assertTrue(service.subscribe(group, "member", "백수").contains("已将 백수 设为群宠"))
        assertEquals(1, queries)
        assertEquals(1, repository.list(group).size)
    }

    @Test fun `disabled groups never query or render and resume automatically when enabled`() = runBlocking<Unit> {
        val repository = GroupPetRepository(root.resolve("polling.db"))
        repository.add(group, "member", snapshot(), clock.millis())
        repository.add(group, "member", snapshot().copy(playerId = 8, nickname = "other"), clock.millis())
        repository.add(other, "member", snapshot(), clock.millis())
        clock.advance(Duration.ofMinutes(2))
        var allow = false
        var permissions = 0
        var queries = 0
        var rendered = 0
        val sent = mutableListOf<String>()
        val service = GroupPetService(repository, { name -> queries++; snapshot(101).copy(playerId = if (name == "other") 8 else 7, nickname = name) },
            { rendered++; "image".toText() }, { target, _ -> sent += target }, clock,
            permission = { target -> permissions++; if (target == other || allow) grantedPermission() else QQGroupPermissionResult(QQGroupPermissionStatus.DISABLED) })
        assertEquals(1, service.tick())
        assertEquals(2, permissions) // Once per group, not per subscribed player.
        assertEquals(1, queries)
        assertEquals(1, rendered)
        assertEquals(listOf(other), sent)
        assertTrue(repository.list(group).all { it.lastMatchId == 100L && it.failures == 0 })
        assertTrue(repository.list(group).all { it.nextCheckAt == clock.millis() + 300_000 })
        allow = true
        clock.advance(Duration.ofMinutes(5))
        assertEquals(2, service.tick())
        assertEquals(2, repository.list(group).size)
        assertTrue(repository.list(group).all { it.lastMatchId == 101L })
    }

    @Test fun `permission failure pauses instead of deleting tasks and parent cancellation escapes`() = runBlocking<Unit> {
        val repository = GroupPetRepository(root.resolve("failed.db"))
        repository.add(group, "member", snapshot(), clock.millis())
        clock.advance(Duration.ofMinutes(2))
        val service = GroupPetService(repository, { error("must not query") }, { error("must not render") }, { _, _ -> error("must not send") }, clock,
            permission = { error("API network failure") })
        assertEquals(0, service.tick())
        assertEquals(100L, repository.list(group).single().lastMatchId)
        val cancelled = GroupPetService(repository, { error("no query") }, { error("no render") }, { _, _ -> }, clock,
            permission = { throw CancellationException("shutdown") })
        assertFailsWith<CancellationException> { cancelled.subscribe(group, "member", "백수") }
    }

    @Test fun `live checker spaces requests and does not cache stale enable state`() = runBlocking<Unit> {
        var nanos = 0L
        var calls = 0
        val waits = mutableListOf<Long>()
        val gate = GroupPetPermissionGate(lookup = {
            calls++; QQGroupPermissionResult(if (calls == 1) QQGroupPermissionStatus.ENABLED else QQGroupPermissionStatus.DISABLED)
        }, pause = { waits += it; nanos += it * 1_000_000 }, nanos = { nanos })
        assertEquals(QQGroupPermissionStatus.ENABLED, gate.query(group).status)
        assertEquals(QQGroupPermissionStatus.DISABLED, gate.query(group).status)
        assertEquals(listOf(2100L), waits)
        assertEquals(2, calls)
    }
}
