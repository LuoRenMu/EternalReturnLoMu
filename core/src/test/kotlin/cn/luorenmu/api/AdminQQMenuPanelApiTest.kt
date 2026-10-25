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
    @Test fun `manual creation JSON uses the validated confirmed route without bypassing unknown field checks`() = testApplication {
        val token = "qq-fixture-token"
        AdminAccessToken.configure(token)
        val gateway = FakeQQMenuPanelClient()
        application { routing { adminQQMenuPanelRouting { AdminQQMenuPanelService(gateway) } } }
        val configuration = """{"scope":"group","target_type":"specific","group_openids":["${"A".repeat(32)}"],"panel":{"items":[{"name":"/帮助","desc":"命令列表","only_admin":true}],"remark":"手动 JSON"}}"""
        val created = client.post("/api/admin/qq/panels") {
            header(AdminAccessToken.HEADER_NAME, token); contentType(ContentType.Application.Json)
            setBody("""{"configuration":$configuration,"confirmed":true}""")
        }
        assertEquals(HttpStatusCode.OK, created.status)
        val record = gateway.records.values.single()
        assertEquals(listOf("A".repeat(32)), record.groupOpenids)
        assertEquals("/帮助", record.panel.items.single().name)
        assertTrue(record.panel.items.single().onlyAdmin)
        assertEquals("手动 JSON", record.panel.remark)
        val invalid = client.post("/api/admin/qq/panels") {
            header(AdminAccessToken.HEADER_NAME, token); contentType(ContentType.Application.Json)
            setBody("""{"configuration":${configuration.dropLast(1)},"unknown":true},"confirmed":true}""")
        }
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        assertEquals(1, gateway.writes)
    }
    @Test fun `only an uncertain mutation warns about possibly applied changes`() = testApplication {
        val token = "qq-fixture-token"
        AdminAccessToken.configure(token)
        val gateway = FakeQQMenuPanelClient().apply { failure = QQMenuPanelRemoteFailure(null) }
        application { routing { adminQQMenuPanelRouting { AdminQQMenuPanelService(gateway) } } }
        val read = client.get("/api/admin/qq/menu") { header(AdminAccessToken.HEADER_NAME, token) }
        assertEquals(HttpStatusCode.BadGateway, read.status)
        assertFalse(read.bodyAsText().contains("修改结果可能已生效"))
        val write = client.put("/api/admin/qq/menu") {
            header(AdminAccessToken.HEADER_NAME, token); contentType(ContentType.Application.Json)
            setBody("""{"menu":null,"confirmed":true}""")
        }
        assertEquals(HttpStatusCode.BadGateway, write.status)
        assertContains(write.bodyAsText(), "修改结果可能已生效")
        assertEquals(0, gateway.writes)
    }
    @Test fun `QQ short and documented limit errors are actionable rather than ambiguous permission failures`() = testApplication {
        val token = "qq-fixture-token"
        AdminAccessToken.configure(token)
        val gateway = FakeQQMenuPanelClient()
        application { routing { adminQQMenuPanelRouting { AdminQQMenuPanelService(gateway) } } }
        for (code in listOf(30013, 40030013)) {
            gateway.failure = QQMenuPanelRemoteFailure(code)
            val response = client.get("/api/admin/qq/menu") { header(AdminAccessToken.HEADER_NAME, token) }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            val body = response.bodyAsText()
            assertContains(body, code.toString())
            assertContains(body, "显示宽度")
            assertFalse(body.contains("修改结果可能已生效"))
            assertFalse(body.contains("请检查接口权限"))
        }
    }
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
