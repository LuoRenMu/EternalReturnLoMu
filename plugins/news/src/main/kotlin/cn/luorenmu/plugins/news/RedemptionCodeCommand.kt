package cn.luorenmu.plugins.news

import cn.luorenmu.Adapter
import cn.luorenmu.command.CommandEvent
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.repository.NewsRepository
import cn.luorenmu.repository.entity.EternalReturnNewsRecord
import cn.luorenmu.service.GameActivityVisibility.displayStatus
import cn.luorenmu.service.GameActivityVisibility.isVisibleOn
import love.forte.simbot.component.qguild.message.QGMarkdown
import love.forte.simbot.message.Message
import org.koin.java.KoinJavaComponent.inject
import java.time.LocalDate

/**
 * @author LoMu
 * Date 2026/8/9
 */
@BotCommand(
    name = "兑换码",
    alias = "兑换码",
    value = "<limit>",
    adapter = [Adapter.QG_BOT, Adapter.ONE_BOT],
)
class RedemptionCodeCommand : CommandEvent {
    override val description = "查询最近识别到的永恒轮回兑换码活动"
    override val example = "/兑换码"
    override val optionals = listOf(
        CommandOptional("limit", "返回数量，默认 5，最多 10", required = false)
    )

    private val newsRepository: NewsRepository by inject(NewsRepository::class.java)

    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message {
        val limit = command["limit"]?.toIntOrNull()?.coerceIn(MIN_LIMIT, MAX_LIMIT) ?: DEFAULT_LIMIT
        val today = LocalDate.now()
        val records = newsRepository.findLatestRedemptionCodes(LOOKUP_LIMIT)
            .filter { it.isVisibleOn(today) }
            .take(limit)

        if (records.isEmpty()) {
            return QGMarkdown.create("## 暂无可用兑换码\n仅显示有效期内或刚过期一天的兑换码活动。")
        }

        return QGMarkdown.create(buildString {
            appendLine("## 可用兑换码")
            records.forEachIndexed { index, record ->
                appendLine()
                appendLine("${index + 1}. **${record.title}**")
                appendLine("兑换码: ${record.code.displayCode()}")
                appendLine("状态: ${record.displayStatus(today)}")
                record.reward?.takeIf { it.isNotBlank() }?.let {
                    appendLine("奖励: ${it.compact()}")
                }
                record.note?.takeIf { it.isNotBlank() && it != record.reward }?.let {
                    appendLine("说明: ${it.compact()}")
                }
                appendLine("有效期: ${record.displayPeriod()}")
            }
        })
    }

    private fun String?.displayCode(): String {
        val code = this?.trim()
        return if (code.isNullOrBlank()) "详见活动页面" else "`$code`"
    }

    private fun EternalReturnNewsRecord.displayPeriod(): String {
        val start = startDate?.takeIf { it.isNotBlank() } ?: "未注明"
        val end = endDate?.takeIf { it.isNotBlank() } ?: "未注明"
        return "$start 至 $end"
    }

    private fun String.compact(maxLength: Int = 120): String {
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
