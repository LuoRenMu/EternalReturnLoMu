package cn.luorenmu.plugins.querystatistics

import cn.luorenmu.Adapter
import cn.luorenmu.command.CommandEvent
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.util.NickNameUtil
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.nutdraw.NutDraw
import cn.luorenmu.repository.StatisticsRepository
import love.forte.simbot.message.Message
import love.forte.simbot.message.OfflineImage
import love.forte.simbot.message.toText
import love.forte.simbot.component.qguild.message.QGMarkdown
import org.koin.java.KoinJavaComponent.inject

@BotCommand(
    "查询统计",
    "查询统计",
    "<nickname>",
    aliases = ["查询记录", "玩家查询统计"],
    adapter = [Adapter.QG_BOT, Adapter.ONE_BOT],
)
/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class QueryStatisticsCommand : CommandEvent {
    override val example = "/查询统计 神圣审判"
    override val description = "查看玩家被查询次数，以及自己查询过的玩家"
    override val optionals = listOf(
        CommandOptional("nickname", "玩家名称；留空只查看自己的查询记录", required = false),
    )

    private val repository: StatisticsRepository by inject(StatisticsRepository::class.java)

    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message {
        val nickname = command["nickname"]?.trim()?.takeIf(String::isNotEmpty)
        if (nickname != null && !NickNameUtil.isValidNickname(nickname)) {
            return "玩家名称不合法".toText()
        }

        if (nickname != null) {
            val senderId = sender.senderOpenId.toString()
            val totalCount = repository.getNicknameQueryCount(nickname)
            val myCount = repository.getPlayerQueryCount(senderId, nickname)
            return QGMarkdown.create("""
                # 玩家查询统计

                **玩家名称：** $nickname
                **累计被查询：** $totalCount 次
                **你查询该玩家：** $myCount 次
            """.trimIndent())
        }

        val data = QueryStatisticsData(
            senderName = sender.senderName,
            history = repository.getPlayerQueryHistory(sender.senderOpenId.toString()),
        )
        val safeSenderId = sender.senderOpenId.toString().replace(Regex("[^A-Za-z0-9._-]"), "_")
        val outputPath = PathUtils.resourcesPathResolve("render", "query-statistics", "$safeSenderId.png")
        NutDraw.render(QueryStatisticsTemplate(), data, outputPath)
        return OfflineImage.fileOfflineImage(outputPath.toString())
    }
}
