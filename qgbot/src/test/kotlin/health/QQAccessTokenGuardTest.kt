package cn.luorenmu.qqbot.health

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import love.forte.simbot.qguild.api.requestData
import love.forte.simbot.qguild.api.user.GetBotInfoApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class QQAccessTokenGuardTest {
    @Test
    fun realSdkRequestFailsWithExpiredCacheAndSucceedsWithTransportGuard() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            if (request.headers[HttpHeaders.Authorization] == "QQBot fixture-fresh")
                respond("""{"id":"fixture-bot","username":"fixture","avatar":"https://example.invalid/avatar.png","bot":true}""", HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            else respond("""{"code":11244,"message":"AccessToken无效或过期"}""", HttpStatusCode.Unauthorized,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            suspend fun sdkRequest() = GetBotInfoApi.requestData(client = client, token = "QQBot fixture-expired",
                server = Url("https://api.sgroup.qq.com"), appId = "fixture-app")
            val before = checkQQAuthentication { sdkRequest() }
            assertFalse(before.healthy)
            assertTrue(before.detail.contains("11244"))
            val guard = QQAccessTokenGuard({ QQTokenLease("fixture-fresh", 7200) })
            guard.refreshNow()
            installQQTokenOverride(client, "api.sgroup.qq.com", guard::currentToken)
            assertTrue(checkQQAuthentication { sdkRequest() }.healthy)
        } finally { client.close() }
    }

    @Test
    fun transientRefreshFailureDoesNotEndJobAndRetryCanRecover() = runBlocking {
        val recovered = CompletableDeferred<Unit>()
        val uncaught = mutableListOf<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> uncaught += e })
        var requests = 0
        val guard = QQAccessTokenGuard(fetch = {
            requests++
            if (requests == 1) error("private credential response")
            QQTokenLease("fixture-recovered", 7200).also { recovered.complete(Unit) }
        }, initialRetryMillis = 10, maxRetryMillis = 50)
        try {
            val job = guard.start(scope)
            assertSame(job, guard.start(scope))
            withTimeout(1500) { recovered.await() }
            withTimeout(1500) { while (guard.currentToken() == null) delay(5) }
            assertTrue(job.isActive)
            assertEquals("fixture-recovered", guard.currentToken())
            assertTrue(requests >= 2)
            assertTrue(uncaught.isEmpty())
            guard.stop()
            withTimeout(1000) { job.join() }
        } finally { scope.cancel() }
    }

    @Test
    fun timeoutAndMalformedResponsesPreserveValidLeaseButNeverExposeExpiredLease() = runBlocking {
        var now = 1000L
        var mode = 0
        val guard = QQAccessTokenGuard(fetch = {
            when (mode) {
                0 -> QQTokenLease("fixture-valid", 10)
                1 -> { delay(5000); QQTokenLease("fixture-too-late", 10) }
                else -> QQTokenLease("", -1)
            }
        }, nowMillis = { now }, requestTimeoutMillis = 30)
        assertTrue(guard.refreshNow())
        mode = 1
        assertFalse(guard.refreshNow())
        assertEquals("fixture-valid", guard.currentToken())
        mode = 2
        assertFalse(guard.refreshNow())
        assertEquals("fixture-valid", guard.currentToken())
        now += 10_000
        assertNull(guard.currentToken())
        assertFalse(QQTokenLease("fixture-secret", 1).toString().contains("fixture-secret"))
    }

    @Test
    fun shutdownCancelsAnInProgressRequestWithoutRetries() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var requests = 0
        val guard = QQAccessTokenGuard(fetch = { requests++; entered.complete(Unit); awaitCancellation() })
        val refresh = async { guard.refreshNow() }
        entered.await()
        refresh.cancelAndJoin()
        assertNull(guard.currentToken())
        assertEquals(1, requests)
    }

    @Test
    fun cancelledSdkChildCanRetryInsteadOfPermanentlyStopping() = runBlocking {
        var fail = true
        val guard = QQAccessTokenGuard(fetch = {
            if (fail) throw CancellationException("SDK child cancelled")
            QQTokenLease("fixture-recovered", 7200)
        })
        assertFalse(guard.refreshNow())
        fail = false
        assertTrue(guard.refreshNow())
    }

    @Test
    fun overrideNeverAddsAuthenticationOrSendsTokenToOtherHosts() = runBlocking {
        val observed = mutableListOf<String?>()
        val client = HttpClient(MockEngine { request ->
            observed += request.headers[HttpHeaders.Authorization]
            respond("{}")
        })
        try {
            installQQTokenOverride(client, "api.sgroup.qq.com") { "fixture-secret" }
            client.get("https://bots.qq.com/app/getAppAccessToken")
            client.get("https://unrelated.example.com/path") { header(HttpHeaders.Authorization, "QQBot original") }
            client.get("https://api.sgroup.qq.com/path") { header(HttpHeaders.Authorization, "Bearer original") }
            client.get("https://api.sgroup.qq.com/path")
            assertEquals(listOf(null, "QQBot original", "Bearer original", null), observed)
        } finally { client.close() }
    }
}
