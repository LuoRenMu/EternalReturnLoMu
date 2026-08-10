package cn.luorenmu.ai

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * OpenAI-compatible LLM client.
 *
 * The class name is kept for compatibility with existing wiring, but the
 * implementation avoids Koog so the bot runtime does not pull in Ktor 3.
 */
class KoogLLMClient(private val config: AIConfig) {

    private val log = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build()
    }

    val isEnabled: Boolean get() = config.apiKey.isNotBlank()

    suspend fun complete(system: String, user: String): String? {
        if (!isEnabled) return null

        val requestBody = ChatCompletionRequest(
            model = config.model,
            messages = listOf(
                ChatMessage(role = "system", content = system),
                ChatMessage(role = "user", content = user),
            ),
        )
        val request = HttpRequest.newBuilder(chatCompletionsUri())
            .timeout(Duration.ofSeconds(120))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
            .build()

        return try {
            val response = withContext(Dispatchers.IO) {
                httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            }
            if (response.statusCode() !in 200..299) {
                log.warn { "AI request failed: status=${response.statusCode()}" }
                return null
            }

            val completion = json.decodeFromString<ChatCompletionResponse>(response.body())
            completion.choices.firstNotNullOfOrNull { choice ->
                choice.message?.content?.takeIf { it.isNotBlank() }
                    ?: choice.text?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            log.error(e) { "AI request failed" }
            null
        }
    }

    private fun chatCompletionsUri(): URI {
        val baseUrl = config.baseUrl.trimEnd('/')
        val apiRoot = if (baseUrl.endsWith("/v1")) baseUrl else "$baseUrl/v1"
        return URI.create("$apiRoot/chat/completions")
    }

    @Serializable
    private data class ChatCompletionRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val stream: Boolean = false,
    )

    @Serializable
    private data class ChatMessage(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class ChatCompletionResponse(
        val choices: List<Choice> = emptyList(),
    )

    @Serializable
    private data class Choice(
        val message: ChatMessage? = null,
        @SerialName("text")
        val text: String? = null,
    )
}
