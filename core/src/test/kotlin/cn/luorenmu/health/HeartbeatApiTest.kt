package cn.luorenmu.health

import cn.luorenmu.api.AdminAccessToken
import cn.luorenmu.api.adminRouting
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HeartbeatApiTest {
    @Test
    fun statusRequiresAdminAuthenticationAndNeverIncludesMailConfiguration() = testApplication {
        AdminAccessToken.configure("heartbeat-test-admin")
        val monitor = CoreHeartbeat(HeartbeatConfig(enabled = true, smtp = SmtpConfig(password = "fixture-secret")),
            "QQ", listOf(HealthCheck("qq-api", "QQ") { HealthResult(true) }), {})
        application {
            attributes.put(CoreHeartbeatKey, monitor)
            routing { adminRouting() }
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/admin/health").status)
        val response = client.get("/api/admin/health") { header("X-Admin-Token", "heartbeat-test-admin") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "starting")
        assertContains(response.bodyAsText(), "qq-api")
        assertFalse(response.bodyAsText().contains("fixture-secret"))
    }
}
