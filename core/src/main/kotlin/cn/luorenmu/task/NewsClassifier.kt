package cn.luorenmu.task

import cn.luorenmu.ConfigFile
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI

/**
 * 使用 DeepSeek API 识别新闻中的兑换码活动。
 * DeepSeek 提供 OpenAI 兼容接口，Koog 依赖已引入，后续可替换为 Koog 的 OpenAILLM 客户端。
 *
 * @author LoMu
 * Date 2026/5/23
 */
class NewsClassifier {

    private val log = KotlinLogging.logger {}
    private val config = ConfigFile.config.ai
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val enabled = config.apiKey.isNotBlank()

    @Serializable
    data class RedemptionCodeResult(
        val isRedemptionCode: Boolean = false,
        val code: String? = null,
        val reward: String? = null,
        val note: String? = null,
    )

    @Serializable
    data class ChatRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Double = 0.1,
        val max_tokens: Int = 256,
    )

    @Serializable
    data class Message(
        val role: String,
        val content: String,
    )

    @Serializable
    data class ChatResponse(
        val choices: List<Choice>,
    ) {
        @Serializable
        data class Choice(
            val message: Message,
        )
    }

    /**
     * 调用 DeepSeek 判断是否为兑换码活动。
     * AI 未启用时返回 null。
     */
    suspend fun classify(title: String, contentText: String): RedemptionCodeResult? {
        if (!enabled) return null

        val prompt = buildString {
            appendLine("判断以下游戏新闻是否为「兑换码/礼包码/补偿码」活动。如果是，提取兑换码、奖励内容和备注。")
            appendLine("只返回 JSON，不要其他文字。格式：")
            appendLine("""{"isRedemptionCode":true/false,"code":"兑换码或null","reward":"奖励描述或null","note":"备注或null"}""")
            appendLine()
            appendLine("标题：$title")
            appendLine("正文：${contentText.take(3000)}")
        }

        return try {
            val request = ChatRequest(
                model = config.model,
                messages = listOf(
                    Message("system", "你是游戏新闻分析助手，只返回 JSON。"),
                    Message("user", prompt),
                )
            )

            val requestJson = json.encodeToString(request)
            val responseJson = httpPost("${config.baseUrl}/v1/chat/completions", requestJson)
            val response = json.decodeFromString<ChatResponse>(responseJson)
            val content = response.choices.firstOrNull()?.message?.content ?: return null

            // DeepSeek 返回的 content 可能包含 markdown 代码块，提取 JSON
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

    private fun httpPost(url: String, body: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000

        connection.outputStream.use { os -> os.write(body.toByteArray()) }

        return connection.inputStream.use { it.readAllBytes().toString(Charsets.UTF_8) }
    }
}
