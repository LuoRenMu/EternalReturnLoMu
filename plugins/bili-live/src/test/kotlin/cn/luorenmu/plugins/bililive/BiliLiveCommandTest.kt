package cn.luorenmu.plugins.bililive

import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import kotlinx.coroutines.*
import love.forte.simbot.common.id.StringID.Companion.ID
import love.forte.simbot.component.qguild.message.QGKeyboards
import love.forte.simbot.component.qguild.message.QGMarkdown
import love.forte.simbot.message.Message
import love.forte.simbot.message.PlainText
import love.forte.simbot.message.Messages
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.*

class BiliLiveCommandTest {
    @TempDir lateinit var root: Path
    private fun Message.plain() = assertIs<PlainText>(this).text
    private val group = "A".repeat(32)
    private val user = "B".repeat(32)
    private fun sender(role: String? = "owner", target: String = group, id: String = user) =
        MessageSender(target.ID, "owner", id.ID, "/主播订阅", "/主播订阅").apply { qqGroupMemberRole = role }
    private fun repository() = BiliLiveRepository { block -> DriverManager.getConnection("jdbc:sqlite:${root.resolve("live.db")}").use(block) }
    private fun monitor(repository: BiliLiveRepository) = BiliLiveMonitor(repository, { error("no upstream call expected") },
        { _,_ -> error("commands do not send active messages") }, permission = { true }, pause = {})

    @Test fun `outside claim group only a trusted owner can use menu list and cancellation`() = runBlocking<Unit> {
        var probes = 0
        val command = BiliLiveCommand({ error("must not touch database or upstream") }, { probes++; true })
        for (role in listOf(null, "member", "admin", "bot", "")) {
            for (action in listOf("", "取消", "列表"))
                assertEquals(LIVE_ACCESS_NOTICE, command.listen(sender(role), mapOf("action" to action, "room" to "22669186")).plain())
        }
        assertEquals(0, probes)
        assertContains(command.listen(sender(target = "not-a-qq-group"), emptyMap()).plain(), LIVE_ACCESS_NOTICE)
        assertContains(command.listen(sender(target = LIVE_CLAIM_GROUP, id = ""), mapOf("action" to "申领")).plain(), LIVE_ACCESS_NOTICE)
    }

    @Test fun `claimed owner with disabled unknown failed or timed out permission receives exact access notice`() = runBlocking<Unit> {
        val repository = repository().apply { claim(LIVE_CLAIM_GROUP, user) }
        val monitor = monitor(repository)
        val command = BiliLiveCommand({ monitor }, { false })
        for (action in listOf("", "订阅", "取消", "列表"))
            assertContains(command.listen(sender(), mapOf("action" to action)).plain(), LIVE_ACCESS_NOTICE)
        val failed = BiliLiveCommand({ monitor }, { error("network error") })
        assertContains(failed.listen(sender(), emptyMap()).plain(), LIVE_ACCESS_NOTICE)
        val timeout = BiliLiveCommand({ monitor }, { withTimeout(1) { delay(100); true } })
        assertContains(timeout.listen(sender(), emptyMap()).plain(), LIVE_ACCESS_NOTICE)
        val cancelled = BiliLiveCommand({ monitor }, { throw CancellationException("unload") })
        assertFailsWith<CancellationException> { cancelled.listen(sender(), emptyMap()) }
        assertTrue(repository.rows().isEmpty())
    }

    @Test fun `ordinary member can claim in either exact allowed group and permission survives reconstruction`() = runBlocking<Unit> {
        val repository = repository()
        var probes = 0
        val command = BiliLiveCommand({ monitor(repository) }, { probes++; error("claims must not query QQ") })
        assertEquals(setOf("C5A63DEC75FA5ACA12306FA2DAF23FC3", "73DE7E7179ABA7BB8A6E83AEAC89D454"), LIVE_CLAIM_GROUPS)
        for ((index, target) in LIVE_CLAIM_GROUPS.withIndex()) {
            val claimant = if (index == 0) user else "D".repeat(32)
            assertEquals(LIVE_CLAIM_SUCCESS, command.listen(sender("member", target, claimant), mapOf("action" to "申领")).plain())
            assertEquals(LIVE_CLAIM_SUCCESS, command.listen(sender(null, target, claimant), mapOf("action" to "申领")).plain())
            assertTrue(repository().hasClaim(claimant))
        }
        assertTrue(repository().hasClaim(user))
        assertFalse(repository().hasClaim("C".repeat(32)), "record sender_id, not group_id or all members")
        assertEquals(0, probes)
        val outside = BiliLiveCommand({ error("wrong group cannot access storage") }, { error("no QQ call") })
        for (target in listOf(group) + LIVE_CLAIM_GROUPS.map { it.lowercase() })
            assertEquals(LIVE_CLAIM_NOTICE, outside.listen(sender(target = target), mapOf("action" to "申领")).plain())
        assertTrue(repository.rows().isEmpty(), "a claim itself must not create a subscription")
    }

    @Test fun `unclaimed users receive exact apology before QQ or upstream calls and cannot borrow another users claim`() = runBlocking<Unit> {
        val repository = repository().apply { claim(LIVE_CLAIM_GROUP, "C".repeat(32)) }
        val command = BiliLiveCommand({ monitor(repository) }, { error("unclaimed users must not query QQ") })
        for (role in listOf(null, "owner", "admin", "member"))
            assertEquals(LIVE_CLAIM_NOTICE, command.listen(sender(role), mapOf("action" to "订阅", "room" to "22669186")).plain())
        assertTrue(repository.rows().isEmpty())
    }

    @Test fun `claimed member and administrator still cannot subscribe in another group`() = runBlocking<Unit> {
        val repository = repository().apply { claim(LIVE_CLAIM_GROUP, user) }
        val command = BiliLiveCommand({ monitor(repository) }, { error("only owner may query QQ") })
        for (role in listOf(null, "admin", "member"))
            assertEquals(LIVE_ACCESS_NOTICE, command.listen(sender(role), mapOf("action" to "订阅", "room" to "22669186")).plain())
        assertTrue(repository.rows().isEmpty())
    }

    @Test fun `Markdown claim button exists in both designated groups and their menus are accessible to ordinary members`() = runBlocking<Unit> {
        var checked = 0
        val command = BiliLiveCommand({ error("menu must not access monitor") }, { checked++; true })
        val claimMenus = LIVE_CLAIM_GROUPS.map { target -> assertIs<Messages>(command.listen(sender("member", target), emptyMap())) }
        assertEquals(0, checked)
        val reply = assertIs<Messages>(command.listen(sender(), emptyMap()))
        assertEquals(1, checked)
        assertEquals(1, reply.filterIsInstance<QGMarkdown>().size)
        fun buttons(message: Messages) = message.filterIsInstance<QGKeyboards>().single().keyboards.content.rows.flatMap { it.buttons }
        val ordinaryButtons = buttons(reply)
        assertEquals(listOf("/主播订阅 订阅 ", "/主播订阅 列表", "/主播订阅 取消 "), ordinaryButtons.map { it.action?.data })
        for (claimMenu in claimMenus) {
            assertEquals(ordinaryButtons.map { it.action?.data } + "/直播通知 申领", buttons(claimMenu).map { it.action?.data })
            assertTrue(buttons(claimMenu).all { it.action?.type == 2 && it.action?.enter == false && it.action?.reply == false })
        }
        val annotation = command.javaClass.getAnnotation(BotCommand::class.java)
        assertEquals(setOf("主播订阅", "直播通知"), (annotation.aliases.toList() + annotation.alias).toSet())
    }

    @Test fun `subscribe uses requested response and canonical id while list and cancellation remain group scoped`() = runBlocking<Unit> {
        val repository = repository().apply { claim(LIVE_CLAIM_GROUP, user) }
        var status = 1
        var fetches = 0
        val monitor = BiliLiveMonitor(repository, { fetches++; BiliRoom(22669186, "title", status, "session", "等咖啡的人") },
            { _,_ -> error("commands do not send active messages") }, permission = { true }, pause = {})
        val command = BiliLiveCommand({ monitor }, { true })
        val response = command.listen(sender(), mapOf("action" to "订阅", "room" to "1"))
        assertEquals("已监听直播间 22669186：等咖啡的人\n当前直播中\n取消：/主播订阅 取消 22669186", response.plain())
        assertContains(command.listen(sender(), mapOf("action" to "列表")).plain(), "22669186")
        assertContains(command.listen(sender(target = "C".repeat(32)), mapOf("action" to "列表")).plain(), "暂无直播监听")
        assertContains(command.listen(sender(), mapOf("action" to "取消", "room" to "22669186")).plain(), "已取消")
        assertEquals(1, fetches, "canonical cancellation must work without an upstream request")
        status = 2
        assertContains(command.listen(sender(), mapOf("action" to "订阅", "room" to "1")).plain(), "当前未开播")
    }
}
