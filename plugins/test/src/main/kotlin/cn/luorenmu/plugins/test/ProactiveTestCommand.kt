package cn.luorenmu.plugins.test

import cn.luorenmu.Adapter
import cn.luorenmu.command.CommandEvent
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.plugins.qqmedia.QQProactiveGroupSender
import cn.luorenmu.plugins.qqmedia.QQGroupPermissionQuery
import cn.luorenmu.plugins.qqmedia.QQGroupPermissionResult
import cn.luorenmu.plugins.qqmedia.QQGroupPermissionStatus
import cn.luorenmu.plugins.qqmedia.SimbotQQRuntime
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import love.forte.simbot.message.Message
import love.forte.simbot.message.toText

internal class ProactiveTestScheduler(
    private val wait: suspend (Long) -> Unit = { delay(it) },
    private val send: suspend (String) -> Unit = { group ->
        QQProactiveGroupSender.send(SimbotQQRuntime.requireBot(), group, "test：这是延迟 5 秒发送的主动消息。".toText())
    },
) {
    private val log = KotlinLogging.logger {}
    private var scope: CoroutineScope? = null
    @Synchronized fun start() { if (scope == null) scope = CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    @Synchronized fun stop() { scope?.cancel(); scope = null }
    @Synchronized fun schedule(group: String): Boolean {
        val active = scope ?: return false
        active.launch {
            try {
                wait(5_000)
                ensureActive()
                withTimeout(60_000) { send(group) }
                log.info { "test 主动消息发送成功" }
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                log.warn { "test 主动发送失败（不自动重试）：${error.javaClass.simpleName}" }
            }
        }
        return true
    }
}

@BotCommand("群主动消息测试", "test", "<action> <room> <target>", adapter = [Adapter.QG_BOT])
internal class ProactiveTestCommand(
    private val scheduler: ProactiveTestScheduler,
    private val live: (() -> BiliLiveMonitor)? = null,
    private val permission: suspend (String) -> QQGroupPermissionResult = {
        QQGroupPermissionQuery.query(SimbotQQRuntime.requireBot(), it)
    },
) : CommandEvent {
    override val description = "主动消息测试；/test 直播 房间号 自动监听 B站开播"
    override val example = "/test；/test 直播 直播间号"
    override val optionals = listOf(
        cn.luorenmu.command.entity.CommandOptional("action", "直播，留空测试主动消息", false),
        cn.luorenmu.command.entity.CommandOptional("room", "房间号、链接、列表或取消", false),
        cn.luorenmu.command.entity.CommandOptional("target", "取消的房间号", false),
    )
    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message {
        val group = sender.groupOpenId.toString()
        if (!group.matches(Regex("[A-Fa-f0-9]{32}"))) return "请在 QQ 群内发送 /test".toText()
        val action = command["action"].orEmpty()
        if (action.isNotBlank()) {
            if (action != "直播") return "用法：/test 或 /test 直播 房间号".toText()
            return try {
                val monitor = live?.invoke() ?: return "直播监听未启动".toText()
                withTimeout(20_000) { monitor.command(group, command["room"].orEmpty(), command["target"].orEmpty()) }.toText()
            } catch (e: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                "直播查询超时，请稍后重试。".toText()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { "直播间查询或存储失败，请检查房间号，或稍后重试。".toText() }
        }
        val result = try {
            withTimeout(8_000) { permission(group) }
        } catch (error: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            QQGroupPermissionResult(QQGroupPermissionStatus.TIMEOUT)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            QQGroupPermissionResult(QQGroupPermissionStatus.FAILED)
        }
        val status = when (result.status) {
            QQGroupPermissionStatus.ENABLED -> "已开启"
            QQGroupPermissionStatus.DISABLED -> "未开启"
            QQGroupPermissionStatus.NO_API_PERMISSION -> "无法确认（11253：机器人未获接口白名单权限）"
            QQGroupPermissionStatus.NOT_PROVIDED -> "无法确认（接口未返回主动消息开关）"
            QQGroupPermissionStatus.TIMEOUT -> "无法确认（查询超时）"
            QQGroupPermissionStatus.FAILED -> "无法确认（接口查询失败${result.errorCode?.let { "，错误码 $it" }.orEmpty()}）"
        }
        val role = when (sender.qqGroupMemberRole) {
            "owner" -> "群主（群主或管理员：是）"
            "admin" -> "管理员（群主或管理员：是）"
            else -> "无法确认（普通成员或 QQ 未提供可靠身份）"
        }
        val scheduled = if (scheduler.schedule(group)) "已安排测试，5 秒后向本群主动发送一条消息。"
            else "测试插件未启动，请稍后重试。"
        return "主动消息：$status\n发送者身份：$role\n$scheduled".toText()
    }
}
