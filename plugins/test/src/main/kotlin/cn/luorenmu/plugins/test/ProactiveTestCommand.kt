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

@BotCommand("群主动消息测试", "test", "<action>", adapter = [Adapter.QG_BOT])
internal class ProactiveTestCommand(
    private val scheduler: ProactiveTestScheduler,
    private val permission: suspend (String) -> QQGroupPermissionResult = {
        QQGroupPermissionQuery.query(SimbotQQRuntime.requireBot(), it)
    },
) : CommandEvent {
    override val description = "主动消息权限及发送者身份诊断，5 秒后主动发送测试消息"
    override val example = "/test"
    override val optionals = listOf(
        cn.luorenmu.command.entity.CommandOptional("action", "留空执行主动消息测试", false),
    )
    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message {
        val group = sender.groupOpenId.toString()
        if (!group.matches(Regex("[A-Fa-f0-9]{32}"))) return "请在 QQ 群内发送 /test".toText()
        val action = command["action"].orEmpty()
        if (action.isNotBlank()) {
            return "直播监听已拆分，请使用 /直播通知 订阅 房间号。/test 仅用于主动消息测试。".toText()
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
