package cn.luorenmu.command

import cn.luorenmu.Adapter
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.request.api.entity.response.dakgg.resolveCutoffs
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import cn.luorenmu.request.entity.module.DakGGServerName
import cn.luorenmu.request.entity.module.DakGGTeamMode
import love.forte.simbot.component.qguild.message.QGMarkdown
import love.forte.simbot.message.Message
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

/**
 *
 * @author LoMu
 * Date 2026/6/3 19:37
 */
@BotCommand(name = "永恒多少分", alias = "永恒多少分", value = "<server>", adapter = [Adapter.QG_BOT, Adapter.ONE_BOT])
class EternalScoreCommand : CommandEvent {
    override val description = "查询永恒/半神当前分段分数线"
    override val example = "/永恒多少分"
    override val optionals = listOf(
        CommandOptional("server", "服务器名称，默认亚1", required = false)
    )

    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message {
        val serverName = command["server"]?.let { DakGGServerName.convert(it) } ?: DakGGServerName.Asia
        val type = EternalReturnDakGGApi.Data.GetCurrentSeason.execute().type
        val leaderboard =
            EternalReturnDakGGApi.Leaderboard.GetLeaderboard(1, type, serverName, DakGGTeamMode.Squad).execute()
        val (eternal, demigod) = leaderboard.cutoffs.resolveCutoffs() ?: (null to null)
        val duration =
            Duration.between(
                LocalDateTime.ofInstant(Date(leaderboard.updatedAt).toInstant(), ZoneId.systemDefault()),
                LocalDateTime.now()
            )

        return QGMarkdown.create(buildString {
            appendLine("# 最近${if (duration.toMinutes() == 0.toLong()) "刚刚" else "${duration.toMinutes()}分钟"}")
            appendLine("**最高**: ${leaderboard.leaderboards.firstOrNull()?.mmr} RP")
            if (eternal != null) appendLine("**永恒**: ${eternal.mmr} RP")
            if (demigod != null) append("**半神**: ${demigod.mmr} RP")
        })
    }
}
