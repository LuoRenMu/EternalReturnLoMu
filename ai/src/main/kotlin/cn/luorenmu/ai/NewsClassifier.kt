package cn.luorenmu.ai

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 使用 DeepSeek API 识别新闻中的兑换码活动。
 * 基于 Koog 客户端实现（原 core 模块裸 HttpURLConnection 实现已迁移至此）。
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
    )

    /**
     * 调用 DeepSeek 判断是否为兑换码活动。
     * AI 未启用时返回 null。
     */
    suspend fun classify(title: String, contentText: String): RedemptionCodeResult? {
        if (!llmClient.isEnabled) return null

        val prompt = buildString {
            appendLine("判断以下游戏新闻是否为「兑换码/礼包码/补偿码」活动。如果是，提取兑换码、奖励内容和备注。")
            appendLine("只返回 JSON，不要其他文字。格式：")
            appendLine("""{"isRedemptionCode":true/false,"code":"兑换码或null","reward":"奖励描述或null","note":"备注或null"}""")
            appendLine()
            appendLine("标题：$title")
            appendLine("正文：${contentText.take(3000)}")
        }

        return try {
            val content = llmClient.complete(
                system = "你是游戏新闻分析助手，只返回 JSON。",
                user = prompt,
            ) ?: return null

            // 模型返回的 content 可能包含 markdown 代码块，提取 JSON
            val jsonBlock = extractJson(content)
            json.decodeFromString<RedemptionCodeResult>(jsonBlock).also { result ->
                if (result.isRedemptionCode) {
                    log.info { "兑换码识别: code=${result.code}, reward=${result.reward}" }
                }
            }
        } catch (e: Exception) {
            log.error(e) { "AI 分类失败: ${e.message}" }
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
