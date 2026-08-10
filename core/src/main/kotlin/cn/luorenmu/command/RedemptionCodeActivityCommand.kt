package cn.luorenmu.command

import cn.luorenmu.Adapter
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.command.entity.RedemptionCodeActivityPage
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.render.BotImageRenderers
import cn.luorenmu.repository.NewsRepository
import cn.luorenmu.repository.entity.EternalReturnNewsRecord
import cn.luorenmu.service.GameActivityVisibility.displayStatus
import cn.luorenmu.service.GameActivityVisibility.isVisibleOn
import love.forte.simbot.component.qguild.message.QGMarkdown
import love.forte.simbot.message.Message
import love.forte.simbot.message.OfflineImage
import org.koin.java.KoinJavaComponent.inject
import java.time.LocalDate

/**
 * @author LoMu
 * Date 2026/8/9
 */
@BotCommand(
    name = "游戏活动",
    alias = "游戏活动",
    value = "<limit>",
    adapter = [Adapter.QG_BOT, Adapter.ONE_BOT],
)
class RedemptionCodeActivityCommand : CommandEvent {
    override val description = "展示可用游戏活动"
    override val example = "/游戏活动"
    override val optionals = listOf(
        CommandOptional("limit", "返回数量，默认 5，最多 10", required = false)
    )

    private val newsRepository: NewsRepository by inject(NewsRepository::class.java)

    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message {
        val limit = command["limit"]?.toIntOrNull()?.coerceIn(MIN_LIMIT, MAX_LIMIT) ?: DEFAULT_LIMIT
        val today = LocalDate.now()
        val records = newsRepository.findLatestGameActivities(LOOKUP_LIMIT)
            .filter { it.isVisibleOn(today) }
            .take(limit)

        if (records.isEmpty()) {
            return QGMarkdown.create("## 暂无可用游戏活动\n仅显示有效期内或刚过期一天的游戏活动。")
        }

        val outputPath = PathUtils.resourcesPathResolve("render", "redemption_code_activity.png")
        BotImageRenderers.get().renderRedemptionCodeActivities(
            RedemptionCodeActivityPage(
                generatedDate = today.toString(),
                items = records.map { it.toPageItem(today) },
            ),
            outputPath,
        )
        return OfflineImage.fileOfflineImage(outputPath.toString())
    }

    private fun EternalReturnNewsRecord.toPageItem(today: LocalDate): RedemptionCodeActivityPage.Item {
        return RedemptionCodeActivityPage.Item(
            title = title,
            code = code?.trim()?.takeIf { it.isNotBlank() },
            reward = reward?.compact() ?: "",
            note = note?.takeIf { it.isNotBlank() && it != reward }?.compact() ?: "",
            period = displayPeriod(),
            status = displayStatus(today),
            thumbnailUrl = thumbnailUrl?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    private fun EternalReturnNewsRecord.displayPeriod(): String {
        val start = startDate?.takeIf { it.isNotBlank() } ?: "未注明"
        val end = endDate?.takeIf { it.isNotBlank() } ?: "未注明"
        return "$start 至 $end"
    }

    private fun String.compact(maxLength: Int = 140): String {
        val normalized = lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        return if (normalized.length <= maxLength) normalized else normalized.take(maxLength) + "..."
    }

    companion object {
        private const val DEFAULT_LIMIT = 5
        private const val MIN_LIMIT = 1
        private const val MAX_LIMIT = 10
        private const val LOOKUP_LIMIT = 50
    }
}
