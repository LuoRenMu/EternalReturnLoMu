package cn.luorenmu.ai.news

import cn.luorenmu.ai.KoogLLMClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup

/**
 * 使用 DeepSeek API 识别新闻中的兑换码活动。
 *
 * @author LoMu
 * Date 2026/8/8
 */
class NewsClassifier(private val llmClient: KoogLLMClient) {

    private val log = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Serializable
    data class RedemptionCodeResult(
        val isRedemptionCode: Boolean = false,
        val code: String? = null,
        val reward: String? = null,
        val note: String? = null,
        val startDate: String? = null,
        val endDate: String? = null,
    )

    /**
     * 调用 AI 判断是否为兑换码活动。
     * AI 未启用时返回 null。
     */
    suspend fun classify(title: String, contentText: String): RedemptionCodeResult? {
        if (!llmClient.isEnabled) return null

        val parse = Jsoup.parse(contentText)
        val content = parse.select(".er-article-detail__content.er-article-content.fr-view").firstOrNull()?.text() ?: contentText
        print(content)
        val prompt = buildString {
            appendLine("判断以下游戏新闻是否为「兑换码/礼包码/补偿码/登录奖励/在线奖励/游玩奖励/页面活动」活动。如果是，提取兑换码、奖励内容和活动详细信息(如果存在跳转链接reward则显示前往活动页面查看详情)")
            appendLine("以中文形式只返回 JSON，不要其他文字包括markdown 。格式：")
            appendLine("""{"isRedemptionCode":true/false,"code":"兑换码或null","reward":"奖励描述或null","note":"活动详细信息或null","startDate:"yyyy-MM-dd格式日期",endDate:"yyyy-MM-dd格式日期  "}""")
            appendLine()
            appendLine("标题：$title")
            appendLine("正文：${content.take(3000)}")
        }

        return try {
            val content = llmClient.complete(
                system = "你是游戏新闻分析助手，只返回 JSON。",
                user = prompt,
            ) ?: return null

            // 模型返回的 content 可能包含 markdown 代码块，提取 JSON
            println(content)
            val jsonBlock = extractJson(content)
            json.decodeFromString<RedemptionCodeResult>(jsonBlock)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** 从 AI 返回的文本中提取 JSON（可能被 ```json ... ``` 包裹）。 */
    private fun extractJson(text: String): String {
        val jsonRegex = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""")
        jsonRegex.find(text)?.let { return it.groupValues[1] }
        return text.trim().trimStart('`').trimEnd('`')
    }
}