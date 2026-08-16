package cn.luorenmu.api

import cn.luorenmu.CoreApplication
import freemarker.cache.ClassTemplateLoader
import io.ktor.client.request.get
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.server.freemarker.FreeMarker
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 *
 * @author LoMu
 * Date 2026/8/16 14:11
 */
class AdminLoginIntegrationTest {
    @Test
    fun `generated console token starts an admin session`() = testApplication {
        val token = AdminAccessToken.regenerate()
        application {
            install(FreeMarker) {
                templateLoader = ClassTemplateLoader(CoreApplication::class.java.classLoader, "templates")
            }
            routing { adminRouting() }
        }
        val clientWithoutRedirects = createClient { followRedirects = false }

        val loginResponse = clientWithoutRedirects.get("/")
        assertEquals(HttpStatusCode.OK, loginResponse.status)
        assertEquals(Charsets.UTF_8, loginResponse.contentType()?.charset())
        assertContains(loginResponse.bodyAsText(), "欢迎回来喵！")
        val response = clientWithoutRedirects.submitForm(
            url = "/admin/login",
            formParameters = parameters { append(AdminAccessToken.QUERY_NAME, "  $token  ") },
        )

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/admin", response.headers[HttpHeaders.Location])
        assertContains(response.headers[HttpHeaders.SetCookie].orEmpty(), AdminAccessToken.COOKIE_NAME)
    }
}
