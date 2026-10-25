package cn.luorenmu.plugins.bililive

import cn.luorenmu.Adapter
import cn.luorenmu.command.CommandEvent
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import kotlinx.coroutines.*
import love.forte.simbot.component.qguild.message.QGKeyboards
import love.forte.simbot.component.qguild.message.QGMarkdown
import love.forte.simbot.message.Message
import love.forte.simbot.message.Messages
import love.forte.simbot.message.toText
import love.forte.simbot.qguild.model.MessageKeyboard

@BotCommand("B站直播通知", "主播订阅", "<action> <room>", aliases = ["直播通知"], adapter = [Adapter.QG_BOT])
internal class BiliLiveCommand(
    private val monitor: () -> BiliLiveMonitor,
    private val permission: suspend (String) -> Boolean,
) : CommandEvent {
    override val description = "申领后由群主订阅 B站开播／下播通知，需要开启 LoMu 主动消息权限"
    override val example = "/直播通知 订阅 22669186"
    override val optionals = listOf(
        CommandOptional("action", "申领、订阅、取消、列表；留空显示按钮", false),
        CommandOptional("room", "直播间房间号", false),
    )

    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message {
        val group = sender.groupOpenId.toString()
        val user = sender.senderOpenId.toString()
        if (!group.matches(Regex("[A-Fa-f0-9]{32}")) || !user.matches(Regex("[A-Fa-f0-9]{32}")))
            return LIVE_ACCESS_NOTICE.toText()
        val action = command["action"].orEmpty().trim()
        return try {
            // A member claims for their own future groups. Neither owner role nor push permission is needed here.
            if (action == "申领") {
                if (group !in LIVE_CLAIM_GROUPS) return LIVE_CLAIM_NOTICE.toText()
                return withTimeout(20_000) { monitor().claim(group, user) }.toText()
            }
            val showMenu = action.isEmpty() || action == "帮助"
            if (showMenu && group in LIVE_CLAIM_GROUPS) return menu(true)
            if (action == "订阅" && !withTimeout(20_000) { monitor().hasClaim(user) }) return LIVE_CLAIM_NOTICE.toText()
            if (!sender.qqGroupMemberRole.equals("owner", true)) return LIVE_ACCESS_NOTICE.toText()
            val enabled = try {
                withTimeout(10_000) { permission(group) }
            } catch (_: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                false
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { false }
            if (!enabled) return LIVE_ACCESS_NOTICE.toText()
            if (showMenu) return menu(false)
            if (action !in setOf("订阅", "取消", "列表")) return LIVE_HELP.toText()
            withTimeout(20_000) { monitor().command(group, user, action, command["room"].orEmpty()) }.toText()
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            "直播查询超时，请稍后重试。".toText()
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { "直播间查询或存储失败，请检查房间号，或稍后重试。".toText() }
    }

    private fun menu(canClaim: Boolean): Message = Messages.of(
        QGMarkdown.create("""## 主播订阅
            |订阅 B站开播／下播提醒，首次订阅正在直播的房间会补发开播提醒。
            |每群、每位用户限 **1 个直播间**，同一用户可在多个群订阅同一个房间。
            |先在指定 Eternal Return 群申领，再由群主开启 LoMu 主动消息权限后订阅。
            |点击按钮填入命令，补充房间号后发送。
        """.trimMargin()),
        QGKeyboards.create(listOf(
            button("subscribe", "订阅主播", "/主播订阅 订阅 "),
            button("list", "查看列表", "/主播订阅 列表"),
            button("cancel", "取消订阅", "/主播订阅 取消 "),
        ) + if (canClaim) listOf(button("claim", "申领权限", "/直播通知 申领")) else emptyList()),
    )

    private fun button(id: String, label: String, data: String): MessageKeyboard =
        MessageKeyboard.builder("bili-live-$id").renderData(label, label, 1)
            .action(type = 2, permission = MessageKeyboard.ActionPermission.AllAccessible,
                data = data, enter = false, reply = false, unsupportTips = "请手动输入 $data").build()
}

internal const val LIVE_HELP = "用法：/直播通知 订阅 房间号\n查看：/主播订阅 列表\n取消：/主播订阅 取消 房间号"
internal const val LIVE_CLAIM_GROUP = "C5A63DEC75FA5ACA12306FA2DAF23FC3"
internal val LIVE_CLAIM_GROUPS = setOf(LIVE_CLAIM_GROUP, "73DE7E7179ABA7BB8A6E83AEAC89D454")
internal const val LIVE_CLAIM_SUCCESS = "现在可以订阅了喵~"
internal const val LIVE_CLAIM_NOTICE = "非常抱歉喵~ 您没有权限，为防止机器人传播至非Eternal Return群 需进行加群领取喵"
