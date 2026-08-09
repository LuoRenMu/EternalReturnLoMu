package cn.luorenmu.ai

/**
 * AI 客户端配置，字段与 core 模块 `BotConfig.AIConfig` 保持一致。
 *
 * 独立定义于本模块，避免 `ai -> core` 反向依赖；由 core 的 Koin 装配从 `ConfigFile.config.ai` 映射。
 *
 * @author LoMu
 * Date 2026/8/8
 */
data class AIConfig(
    val apiKey: String = "",
    val model: String = "Qwen/Qwen3-Omni-30B-A3B-Instruct",
    val baseUrl: String = "https://api.siliconflow.cn",
)
