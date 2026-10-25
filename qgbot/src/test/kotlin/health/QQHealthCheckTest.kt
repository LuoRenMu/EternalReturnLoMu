package cn.luorenmu.qqbot.health

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import love.forte.simbot.qguild.ErrInfo
import love.forte.simbot.qguild.QQGuildApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QQHealthCheckTest {
    @Test
    fun actuallyCallsSdkProbeAndDetectsExpiredCachedTokenWithoutResponseLeak() = runBlocking {
        var calls = 0
        val result = checkQQAuthentication {
            calls++
            throw QQGuildApiException(ErrInfo(11244, "private response token=secret"), 401, "Unauthorized")
        }
        assertEquals(1, calls)
        assertFalse(result.healthy)
        assertTrue(result.detail.contains("11244"))
        assertFalse(result.detail.contains("secret"))
    }

    @Test
    fun validSdkAuthenticationIsHealthy() = runBlocking {
        assertTrue(checkQQAuthentication { Unit }.healthy)
    }

    @Test
    fun cancellationIsNotReportedAsAuthenticationFailure() {
        runBlocking {
            assertFailsWith<CancellationException> {
                checkQQAuthentication { throw CancellationException("shutdown") }
            }
        }
    }

    @Test
    fun structuredApiErrorDoesNotRequireParsingAndUnknownErrorUsesHttpCode() = runBlocking {
        val structured = checkQQAuthentication {
            throw QQGuildApiException(ErrInfo(11244, "secret"), 401, "Unauthorized", "secret")
        }
        assertTrue(structured.detail.contains("11244"))
        val http = checkQQAuthentication { throw QQGuildApiException(503, "secret") }
        assertTrue(http.detail.contains("503"))
        assertFalse(http.detail.contains("secret"))
    }
}
