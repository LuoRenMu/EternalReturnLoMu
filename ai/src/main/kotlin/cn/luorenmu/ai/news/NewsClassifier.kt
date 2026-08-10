package cn.luorenmu.ai.news

import cn.luorenmu.ai.KoogLLMClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup

/**
 * 使用 AI 识别新闻中的游戏活动。
 *
 * @author LoMu
 * Date 2026/8/8
 */
class NewsClassifier(private val llmClient: KoogLLMClient) {

    private val log = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Serializable
    data class RedemptionCodeResult(
        val isGameActivity: Boolean = false,
        val isRedemptionCode: Boolean = false,
        val code: String? = null,
        val reward: String? = null,
        val note: String? = null,
        val startDate: String? = null,
        val endDate: String? = null,
    )

    /**
     * 调用 AI 判断是否为游戏活动。
     * AI 未启用时返回 null。
     */
    suspend fun classify(title: String, contentText: String): RedemptionCodeResult? {
        if (!llmClient.isEnabled) return null

        val parse = Jsoup.parse(contentText)
        val content = parse.select(".er-article-detail__content.er-article-content.fr-view").firstOrNull()?.text() ?: contentText
        val prompt = buildString {
            appendLine("判断以下永恒轮回官方新闻是否为「游戏活动」。")
            appendLine("游戏活动包括：兑换码/礼包码/补偿码、登录奖励、在线奖励、游玩奖励、网页活动、赛事活动、签到活动、通行证/商店限时活动、社区活动、官方运营活动。")
            appendLine("纯公告、版本更新、平衡调整、维护通知、已知问题、处罚公告如果没有可参与活动或奖励，则不是游戏活动。")
            appendLine("如果是游戏活动，提取兑换码、奖励内容、活动说明、活动开始日期与结束日期。没有兑换码但需要前往活动页面时，code 返回 null，在 note 中说明前往活动页面参与。")
            appendLine("以中文形式只返回 JSON，不要其他文字包括markdown 。格式：")
            appendLine("""{"isGameActivity":true/false,"isRedemptionCode":true/false,"code":"兑换码或null","reward":"奖励描述或null","note":"活动详细信息或null","startDate":"yyyy-MM-dd格式日期或null","endDate":"yyyy-MM-dd格式日期或null"}""")
            appendLine()
            appendLine("标题：$title")
            appendLine("正文：${content.take(3000)}")
        }

        return try {
            val content = llmClient.complete(
                system = "你是游戏新闻分析助手，只返回 JSON。",
                user = prompt,
            ) ?: return null

            val jsonBlock = extractJson(content)
            json.decodeFromString<RedemptionCodeResult>(jsonBlock)
        } catch (e: Exception) {
            log.error(e) { "新闻活动识别失败: $title" }
            null
        }
    }
    // 由于使用的是硅基流动较老的模型 请求参数尚未找到和deepseek强制要求返回 json object
    private fun extractJson(text: String): String {
        val jsonRegex = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""")
        jsonRegex.find(text)?.let { return it.groupValues[1] }
        val trimmed = text.trim().trimStart('`').trimEnd('`')
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start in 0..end) trimmed.substring(start, end + 1) else trimmed
    }
}
