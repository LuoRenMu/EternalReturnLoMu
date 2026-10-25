package cn.luorenmu.plugins.grouppet

import cn.luorenmu.Adapter
import cn.luorenmu.command.CommandEvent
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.util.NickNameUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import love.forte.simbot.component.qguild.message.QGMarkdown
import love.forte.simbot.component.qguild.message.QGKeyboards
import love.forte.simbot.qguild.model.MessageKeyboard
import love.forte.simbot.message.Messages
import love.forte.simbot.message.Message
import love.forte.simbot.message.toText

@BotCommand("群宠战绩订阅", "群宠", "<player> <nickname>", adapter = [Adapter.QG_BOT])
internal class GroupPetCommand(private val service: () -> GroupPetService) : CommandEvent {
    private val log = io.github.oshai.kotlinlogging.KotlinLogging.logger {}
    override val description = "订阅玩家新对局并主动发到本群，按活跃度调整检查间隔；支持列表、取消"
    override val example = "/群宠 백수"
    override val optionals = listOf(
        CommandOptional("player", "玩家名称，或 列表 / 取消", false),
        CommandOptional("nickname", "取消订阅时填写玩家名称或列表中的订阅ID", false),
    )

    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message = withContext(Dispatchers.IO) {
        val group = sender.groupOpenId.toString()
        if (!group.matches(Regex("[A-Fa-f0-9]{32}"))) return@withContext "请在 QQ 群内使用群宠命令".toText()
        val player = command["player"]?.trim().orEmpty()
        val nickname = command["nickname"]?.trim().orEmpty()
        when {
            player.isBlank() || player == "帮助" -> menu()
            player == "列表" && nickname.isEmpty() -> {
                val rows = service().list(group)
                if (rows.isEmpty()) "本群暂无群宠，使用 /群宠 玩家名 添加".toText()
                else rows.joinToString("\n") { it.nickname }.toText()
            }
            player == "取消" -> {
                if (nickname.isBlank()) "用法：/群宠 取消 玩家名或订阅ID".toText()
                else (if (service().remove(group, nickname)) "已取消本群的该玩家订阅" else "本群未订阅该玩家").toText()
            }
            nickname.isNotEmpty() -> HELP.toText()
            !NickNameUtil.isValidNickname(player) -> "玩家名称格式不合法，请检查后重试。".toText()
            else -> try {
                service().subscribe(group, sender.senderOpenId.toString(), player).toText()
            } catch (error: PetPlayerNotFoundException) {
                "玩家不存在，请检查玩家名称。未建立订阅。".toText()
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                log.warn { "群宠订阅失败：${error.javaClass.simpleName}，位置=${error.stackTrace.firstOrNull()}" }
                (if (error is IllegalArgumentException && error.message == "每群最多订阅 3 位群宠，请先取消旧订阅") error.message!!
                else if (error.message == "PlayerSyncRateLimited") "战绩同步接口限流，请稍后重试。未建立新订阅。"
                else "订阅暂时失败：战绩服务或存储异常，请稍后重试。未建立新订阅。").toText()
            }
        }
    }

    private fun menu(): Message = Messages.of(
        QGMarkdown.create("""## 群宠战绩订阅
            |发现新对局后向本群发送最新对局；首次订阅不推送历史战绩。
            |
            |每群最多订阅 **3 位玩家**。
            |**有效期 7 天**，到期停止轮询，可重新订阅；重复命令不延长有效期。
            |
            |点击按钮填入命令，补充玩家名后发送。
        """.trimMargin()),
        QGKeyboards.create(listOf(
            menuButton("subscribe", "订阅玩家", "/群宠 "),
            menuButton("list", "查看列表", "/群宠 列表"),
            menuButton("cancel", "取消订阅", "/群宠 取消 "),
        )),
    )

    private fun menuButton(id: String, label: String, command: String): MessageKeyboard =
        MessageKeyboard.builder("group-pet-$id")
            .renderData(label, label, 1)
            .action(type = 2, permission = MessageKeyboard.ActionPermission.AllAccessible,
                data = command, enter = false, reply = false,
                unsupportTips = "请手动输入 $command")
            .build()

    private companion object {
        const val HELP = "用法：/群宠 玩家名\n查看：/群宠 列表\n取消：/群宠 取消 玩家名\n新对局会主动发到本群；首次订阅不推送历史对局。"
    }
}
