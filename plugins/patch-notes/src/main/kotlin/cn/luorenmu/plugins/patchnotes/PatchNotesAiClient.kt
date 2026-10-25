package cn.luorenmu.plugins.patchnotes

import cn.luorenmu.ai.AIConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Patch-specific request limits stay in this plugin; the host's AI client/config are unchanged. */
class PatchNotesAiClient(
    private val config: AIConfig,
    private val timeout: Duration = Duration.ofSeconds(300),
    private val send: suspend (HttpRequest) -> Pair<Int, String> = { request -> sendCancellable(request) },
) {
    private val log = KotlinLogging.logger {}
    val isEnabled get() = config.apiKey.isNotBlank()

    internal fun payload(system: String, user: String): String = buildJsonObject {
        put("model", config.model)
        put("stream", false)
        putJsonArray("messages") {
            add(buildJsonObject { put("role", "system"); put("content", system) })
            add(buildJsonObject { put("role", "user"); put("content", user) })
        }
        // SiliconFlow's max_tokens excludes reasoning; cap both budgets separately.
        // https://docs.siliconflow.cn/docs/api/chat-completions-post
        if (URI(config.baseUrl).host == "api.siliconflow.cn" &&
            config.model.startsWith("Qwen/") && config.model.contains("Thinking", ignoreCase = true)) {
            put("thinking_budget", 1024)
            put("max_tokens", 4096)
        }
    }.toString()

    suspend fun complete(system: String, user: String): String? {
        if (!isEnabled) return null
        val root = config.baseUrl.trimEnd('/').let { if (it.endsWith("/v1")) it else "$it/v1" }
        val request = HttpRequest.newBuilder(URI("$root/chat/completions"))
            .timeout(timeout).header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload(system, user))).build()
        return try {
            val (status, body) = send(request)
            if (status !in 200..299) {
                log.warn { "最新更新AI请求失败：HTTP $status" }
                return null
            }
            val choices = Json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray ?: return null
            choices.firstNotNullOfOrNull { choice ->
                val item = choice.jsonObject
                if (item["finish_reason"]?.jsonPrimitive?.contentOrNull == "length") null else {
                    item["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: item["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            // Do not log request bodies, API keys, article text, or raw provider responses.
            log.warn { "最新更新AI请求失败：${e.javaClass.simpleName}" }
            null
        }
    }

    companion object {
        private val httpClient by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build() }
        private suspend fun sendCancellable(request: HttpRequest): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
            val future = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            continuation.invokeOnCancellation { future.cancel(true) }
            future.whenComplete { response, error ->
                if (error != null) continuation.resumeWithException(error.cause ?: error)
                else continuation.resume(response.statusCode() to response.body())
            }
        }
    }
}
