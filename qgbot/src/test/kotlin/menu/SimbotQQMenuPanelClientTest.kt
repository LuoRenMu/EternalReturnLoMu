package cn.luorenmu.qqbot.menu

import cn.luorenmu.qq.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.request
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import love.forte.simbot.component.qguild.bot.QGBot
import love.forte.simbot.qguild.api.QQGuildApi
import love.forte.simbot.qguild.api.QQGuildApiWithoutResult
import love.forte.simbot.qguild.api.requestData
import java.lang.reflect.Proxy
import java.io.IOException
import kotlin.test.*

class SimbotQQMenuPanelClientTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun response(method: HttpMethod, path: String): String = when {
        path == "/v2/menu" && method == HttpMethod.Get -> """{"version":1,"menu":null}"""
        path == "/v2/panels" && method == HttpMethod.Get -> """{"records":[],"next_cursor":"next","is_end":false}"""
        path == "/v2/panels" -> """{"panel_id":"new-panel"}"""
        path == "/v2/panels/p1" && method == HttpMethod.Get -> """{"panel_id":"p1","scope":"group","target_type":"specific","panel":{"items":[]}}"""
        else -> """{"version":2}"""
    }

    @Test fun `all operations execute via Simbot and serialize real SDK HTTP methods bodies pagination and empty responses`() = runBlocking {
        val apis = mutableListOf<QQGuildApi<*>>()
        val bot = Proxy.newProxyInstance(QGBot::class.java.classLoader, arrayOf(QGBot::class.java)) { _, method, args ->
            when (method.name) {
                "isActive" -> true
                "executeData" -> {
                    val api = args!![0] as QQGuildApi<*>
                    apis += api
                    if (api is QQGuildApiWithoutResult) Unit
                    else json.decodeFromString(api.resultDeserializationStrategy, response(api.method, api.url.encodedPath))
                }
                else -> error("Unexpected SDK call: ${method.name}")
            }
        } as QGBot
        val gateway = SimbotQQMenuPanelClient { bot }
        val menu = QQCustomMenu(listOf(QQMenuItem("帮助", "send_message", "/帮助"),
            QQMenuItem("通知", "switch", switch = QQMenuSwitch("notice", true))))
        val panel = QQCommandPanel(listOf(QQPanelItem("/帮助", "命令列表", onlyAdmin = true)))
        assertTrue(gateway.available)
        assertNull(gateway.getMenu().menu)
        gateway.putMenu(null); gateway.putMenu(menu)
        gateway.listPanels("group", "cursor+/=&?", 20)
        gateway.getPanel("p1")
        assertEquals("new-panel", gateway.createPanel(QQPanelCreate("group", "specific", panel, groupOpenids = listOf("A".repeat(32)))).panelId)
        gateway.putPanel("p1", panel)
        gateway.putTargets("p1", QQPanelTargets("del", groupOpenids = listOf("A".repeat(32))))
        gateway.deletePanel("p1")
        val wireBodies = mutableListOf<JsonObject>()
        var requests = 0
        HttpClient(MockEngine { request ->
            val api = apis[requests++]
            assertEquals(api.method, request.method)
            assertEquals(api.url.encodedPath, request.url.encodedPath)
            if (api.method == HttpMethod.Get && api.url.encodedPath == "/v2/panels") {
                assertEquals("cursor+/=&?", request.url.parameters["cursor"])
                assertEquals("group", request.url.parameters["scope"])
                assertEquals("20", request.url.parameters["limit"])
            }
            if (request.body is OutgoingContent.ByteArrayContent) {
                wireBodies += Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
            }
            respond(if (api is QQGuildApiWithoutResult) "" else response(api.method, api.url.encodedPath),
                if (api is QQGuildApiWithoutResult) HttpStatusCode.NoContent else HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json() } }.use { client ->
            for (api in apis) api.requestData(client = client, token = null, server = null, appId = null)
        }
        assertEquals(9, requests)
        assertEquals(JsonNull, wireBodies[0].getValue("menu"), "clear must transmit an explicit null, not an omitted body")
        assertEquals("/帮助", wireBodies[1].getValue("menu").jsonObject.getValue("items").jsonArray[0].jsonObject.getValue("send_message").jsonPrimitive.content)
        assertEquals(true, wireBodies[1].getValue("menu").jsonObject.getValue("items").jsonArray[1].jsonObject.getValue("switch").jsonObject.getValue("default").jsonPrimitive.boolean)
        assertEquals("specific", wireBodies[2].getValue("target_type").jsonPrimitive.content)
        assertTrue(wireBodies[3].containsKey("panel"))
        assertEquals("del", wireBodies[4].getValue("op").jsonPrimitive.content)
        assertTrue(wireBodies.none { "confirmed" in it }, "admin confirmation must never leak into the QQ protocol")
    }

    @Test fun `ambiguous managed mutations are never automatically replayed on a server error`() = runBlocking {
        for ((method, path, expected) in listOf(Triple(HttpMethod.Post, "/v2/panels", 1), Triple(HttpMethod.Put, "/v2/menu", 1),
            Triple(HttpMethod.Put, "/v2/panels/p1/target", 1), Triple(HttpMethod.Delete, "/v2/panels/p1", 1),
            Triple(HttpMethod.Get, "/v2/menu", 4), Triple(HttpMethod.Post, "/v2/groups/fixture/messages", 4))) {
            var count = 0
            HttpClient(MockEngine { count++; respond("{}", HttpStatusCode.InternalServerError) }) {
                install(HttpRequestRetry) {
                    maxRetries = 3; delayMillis { 0 }
                    retryIf { request, response -> shouldRetryQQApiRequest(request.method, request.url.encodedPath, response.status.value) }
                }
            }.use { client -> client.request("https://api.sgroup.qq.com$path") { this.method = method } }
            assertEquals(expected, count, "$method $path")
        }
        assertFalse(shouldRetryQQApiRequest(HttpMethod.Get, "/v2/menu", 429))
    }

    @Test fun `unconnected gateway is unavailable without issuing any SDK operation`() = runBlocking {
        val gateway = SimbotQQMenuPanelClient { null }
        assertFalse(gateway.available)
        assertFailsWith<QQMenuPanelUnavailable> { gateway.getMenu() }
    }

    @Test fun `connection failures do not replay writes while ordinary SDK retry behavior remains unchanged`() = runBlocking {
        for ((method, path, expected) in listOf(Triple(HttpMethod.Post, "/v2/panels", 1),
            Triple(HttpMethod.Put, "/v2/menu", 1), Triple(HttpMethod.Delete, "/v2/panels/p1", 1),
            Triple(HttpMethod.Get, "/v2/panels", 4), Triple(HttpMethod.Post, "/v2/groups/fixture/messages", 4))) {
            var attempts = 0
            HttpClient(MockEngine { attempts++; throw IOException("connection lost after write") }) {
                install(HttpRequestRetry) {
                    maxRetries = 3; delayMillis { 0 }
                    retryOnExceptionIf { request, cause -> shouldRetryQQApiException(request.method, request.url.build().encodedPath, cause) }
                }
            }.use { client ->
                assertFailsWith<IOException> { client.request("https://api.sgroup.qq.com$path") { this.method = method } }
            }
            assertEquals(expected, attempts, "$method $path")
        }
        assertFalse(shouldRetryQQApiException(HttpMethod.Get, "/v2/menu", kotlinx.coroutines.CancellationException()))
        assertFalse(shouldRetryQQApiException(HttpMethod.Get, "/v2/menu", io.ktor.client.network.sockets.SocketTimeoutException("fixture", null)))
        // Ktor 2's default distinguishes its timeout wrapper from a plain java.net exception.
        assertTrue(shouldRetryQQApiException(HttpMethod.Get, "/v2/menu", java.net.SocketTimeoutException()))
    }
}
