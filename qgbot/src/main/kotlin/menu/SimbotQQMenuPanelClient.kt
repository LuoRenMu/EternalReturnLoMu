package cn.luorenmu.qqbot.menu

import cn.luorenmu.qq.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import love.forte.simbot.component.qguild.bot.QGBot
import love.forte.simbot.qguild.QQGuildApiException
import love.forte.simbot.qguild.api.*
import java.io.IOException

/** PR #391-compatible operations using the installed Simbot 4.5 client, token guard and API server. */
class SimbotQQMenuPanelClient(private val botProvider: () -> QGBot?) : QQMenuPanelClient {
    override val available: Boolean get() = botProvider()?.isActive == true
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    private suspend fun <R : Any> execute(api: QQGuildApi<R>): R {
        val bot = botProvider()?.takeIf { it.isActive } ?: throw QQMenuPanelUnavailable("QQ 机器人未连接。")
        return try { bot.executeData(api)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: QQGuildApiException) {
            val code = error.info?.code ?: Regex("response info: ErrInfo\\(code=([0-9]+)")
                .find(error.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: error.value
            throw QQMenuPanelRemoteFailure(code)
        } catch (_: IOException) { throw QQMenuPanelRemoteFailure(null)
        } catch (_: SerializationException) { throw QQMenuPanelRemoteFailure(null) }
    }

    override suspend fun getMenu() = execute(MenuGetApi(arrayOf("v2", "menu"), QQMenuSnapshot.serializer()))
    override suspend fun putMenu(menu: QQCustomMenu?) = execute(MenuPutApi(arrayOf("v2", "menu"), buildJsonObject {
        // A missing field is not a clear operation. Preserve this explicit null independently of SDK settings.
        put("menu", menu?.let { json.encodeToJsonElement(it) } ?: JsonNull)
    }, QQVersion.serializer()))
    override suspend fun listPanels(scope: String, cursor: String?, limit: Int) = execute(MenuGetApi(
        arrayOf("v2", "panels"), QQPanelPage.serializer(), buildMap {
            put("scope", scope); put("limit", limit.toString()); cursor?.let { put("cursor", it) }
        }))
    override suspend fun getPanel(id: String) = execute(MenuGetApi(arrayOf("v2", "panels", id), QQPanelRecord.serializer()))
    override suspend fun createPanel(configuration: QQPanelCreate) = execute(MenuPostApi(
        arrayOf("v2", "panels"), json.encodeToJsonElement(configuration).jsonObject, QQPanelCreated.serializer()))
    override suspend fun putPanel(id: String, panel: QQCommandPanel) = execute(MenuPutApi(
        arrayOf("v2", "panels", id), buildJsonObject { put("panel", json.encodeToJsonElement(panel)) }, QQVersion.serializer()))
    override suspend fun putTargets(id: String, targets: QQPanelTargets) {
        execute(MenuTargetApi(arrayOf("v2", "panels", id, "target"), json.encodeToJsonElement(targets).jsonObject))
    }
    override suspend fun deletePanel(id: String) { execute(MenuDeleteApi(arrayOf("v2", "panels", id))) }
}

internal class MenuGetApi<R : Any>(override val path: Array<String>,
    override val resultDeserializationStrategy: DeserializationStrategy<R>,
    private val query: Map<String, String> = emptyMap()) : GetQQGuildApi<R>() {
    override fun URLBuilder.buildUrl() { query.forEach { (key, value) -> parameters.append(key, value) } }
}
internal class MenuPutApi<R : Any>(override val path: Array<String>, private val payload: JsonObject,
    override val resultDeserializationStrategy: DeserializationStrategy<R>) : PutQQGuildApi<R>() {
    override fun createBody(): Any = wireBody(payload)
}
internal class MenuPostApi<R : Any>(override val path: Array<String>, private val payload: JsonObject,
    override val resultDeserializationStrategy: DeserializationStrategy<R>) : PostQQGuildApi<R>() {
    override fun createBody(): Any = wireBody(payload)
}
internal class MenuTargetApi(override val path: Array<String>, private val payload: JsonObject) :
    PutQQGuildApi<Unit>(), QQGuildApiWithoutResult {
    override fun createBody(): Any = wireBody(payload)
}
internal class MenuDeleteApi(override val path: Array<String>) : DeleteQQGuildApi<Unit>(), QQGuildApiWithoutResult

// SDK 4.5 exposes request bodies as Any. Pre-encode JSON to avoid Ktor 2 guessing JsonLiteral's serializer.
private fun wireBody(payload: JsonObject) = TextContent(payload.toString(), ContentType.Application.Json)

/** Do not automatically replay an ambiguous menu/panel mutation (especially panel creation). */
internal fun shouldRetryQQApiRequest(method: HttpMethod, path: String, status: Int): Boolean {
    return status in 500..599 && !isMenuMutation(method, path)
}

internal fun shouldRetryQQApiException(method: HttpMethod, path: String, cause: Throwable): Boolean =
    !isMenuMutation(method, path) && cause !is CancellationException &&
        cause !is HttpRequestTimeoutException && cause !is ConnectTimeoutException && cause !is SocketTimeoutException

private fun isMenuMutation(method: HttpMethod, path: String): Boolean = method != HttpMethod.Get &&
    (path == "/v2/menu" || path == "/v2/panels" || path.startsWith("/v2/panels/"))
