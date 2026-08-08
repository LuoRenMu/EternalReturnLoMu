package cn.luorenmu.ai

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.MessagePart

/**
 * 基于 JetBrains Koog 的 LLM 客户端封装。
 *
 * 面向 OpenAI 兼容接口（如 DeepSeek），通过 [AIConfig] 的 baseUrl 指向目标服务。
 * AI 未配置（apiKey 为空）时 [complete] 直接返回 null。
 *
 * @author LoMu
 * Date 2026/8/8
 */
class KoogLLMClient(private val config: AIConfig) {

    /** AI 是否启用（apiKey 非空）。 */
    val isEnabled: Boolean get() = config.apiKey.isNotBlank()

    private val executor: PromptExecutor by lazy {
        val client = OpenAILLMClient(
            apiKey = config.apiKey,
            settings = OpenAIClientSettings(baseUrl = config.baseUrl),
        )
        // 将 OpenAI 兼容客户端注册到 DeepSeek provider 名下，配合自定义模型名使用
        MultiLLMPromptExecutor(LLMProvider.DeepSeek to client)
    }

    private val model: LLModel by lazy {
        LLModel(provider = LLMProvider.DeepSeek, id = config.model, capabilities = emptyList())
    }

    /** 单轮对话，返回拼接后的文本内容；AI 未启用或调用失败时返回 null。 */
    suspend fun complete(system: String, user: String): String? {
        if (!isEnabled) return null
        val prompt = prompt("chat") {
            system(system)
            user(user)
        }
        val response = executor.execute(prompt, model)
        return response.parts
            .filterIsInstance<MessagePart.Text>()
            .joinToString("") { it.text }
    }
}
