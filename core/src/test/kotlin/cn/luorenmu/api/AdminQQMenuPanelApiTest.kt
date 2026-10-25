package cn.luorenmu.api

import cn.luorenmu.qq.QQMenuPanelRemoteFailure
import cn.luorenmu.service.AdminQQMenuPanelService
import cn.luorenmu.service.FakeQQMenuPanelClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.*

class AdminQQMenuPanelApiTest {
    @Test fun `every QQ route requires admin before reading a body or invoking the SDK`() = testApplication {
        AdminAccessToken.configure("qq-fixture-token")
        val gateway = FakeQQMenuPanelClient()
        application { routing { adminQQMenuPanelRouting { AdminQQMenuPanelService(gateway) } } }
        for ((method, path) in listOf(HttpMethod.Get to "status", HttpMethod.Get to "menu", HttpMethod.Put to "menu",
            HttpMethod.Get to "panels", HttpMethod.Post to "panels", HttpMethod.Get to "panels/p1",
            HttpMethod.Put to "panels/p1", HttpMethod.Put to "panels/p1/target", HttpMethod.Delete to "panels/p1")) {
            assertEquals(HttpStatusCode.Unauthorized, client.request("/api/admin/qq/$path") { this.method = method; setBody("invalid-json") }.status)
        }
        assertEquals(0, gateway.writes)
    }

    @Test fun `authenticated requests preserve explicit null and reject malformed unconfirmed or oversized writes`() = testApplication {
        val token = "qq-fixture-token"
        AdminAccessToken.configure(token)
        val gateway = FakeQQMenuPanelClient()
        application { routing { adminQQMenuPanelRouting { AdminQQMenuPanelService(gateway) } } }
        for (body in listOf("{}", "not-json", """{"menu":null}""", """{"menu":null,"confirmed":true,"typo":true}""", "x".repeat(65_537))) {
            val response = client.put("/api/admin/qq/menu") { header(AdminAccessToken.HEADER_NAME, token); contentType(ContentType.Application.Json); setBody(body) }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
        assertEquals(0, gateway.writes)
        assertEquals(HttpStatusCode.BadRequest, client.put("/api/admin/qq/menu") {
            header(AdminAccessToken.HEADER_NAME, token); setBody("""{"menu":null,"confirmed":true}""")
        }.status, "simple cross-site text/form submissions must not mutate QQ configuration")
        val clear = client.put("/api/admin/qq/menu") {
            header(AdminAccessToken.HEADER_NAME, token); contentType(ContentType.Application.Json); setBody("""{"menu":null,"confirmed":true}""")
        }
        assertEquals(HttpStatusCode.OK, clear.status)
        assertNull(gateway.currentMenu)
        assertEquals(1, gateway.writes)
        assertEquals(HttpStatusCode.BadRequest, client.get("/api/admin/qq/panels?limit=garbage") { header(AdminAccessToken.HEADER_NAME, token) }.status)
    }

    @Test fun `unsupported adapters and upstream permissions surface useful errors without secrets`() = testApplication {
        val token = "qq-fixture-token"
        AdminAccessToken.configure(token)
        val gateway = FakeQQMenuPanelClient()
        gateway.available = false
        application { routing { adminQQMenuPanelRouting { AdminQQMenuPanelService(gateway) } } }
        val status = client.get("/api/admin/qq/status") { header(AdminAccessToken.HEADER_NAME, token) }
        assertContains(status.bodyAsText(), "\"available\":false")
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/api/admin/qq/menu") { header(AdminAccessToken.HEADER_NAME, token) }.status)
        gateway.available = true
        gateway.failure = QQMenuPanelRemoteFailure(11253)
        val failed = client.get("/api/admin/qq/menu") { header(AdminAccessToken.HEADER_NAME, token) }
        assertEquals(HttpStatusCode.BadGateway, failed.status)
        assertContains(failed.bodyAsText(), "11253")
        assertFalse(failed.bodyAsText().contains(token))
    }
}
