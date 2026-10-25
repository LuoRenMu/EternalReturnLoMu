package cn.luorenmu.health

import cn.luorenmu.ConfigFile
import cn.luorenmu.common.util.DatabaseBackend
import cn.luorenmu.common.util.DatabaseManager
import cn.luorenmu.service.AdminConfigService
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmtpHealthNotifierTest {
    private val config = SmtpConfig(user = "sender@example.com", password = "fixture-secret", to = "admin@example.com")

    @Test
    fun usesVerifiedTlsAndFiniteSocketTimeoutsWithoutPasswordsInProperties() {
        val notifier = SmtpHealthNotifier(config)
        val props = notifier.smtpProperties(config)
        assertEquals("true", props["mail.smtp.ssl.enable"])
        assertEquals("true", props["mail.smtp.ssl.checkserveridentity"])
        listOf("connectiontimeout", "timeout", "writetimeout").forEach {
            assertEquals("10000", props["mail.smtp.$it"])
        }
        assertFalse(props.toString().contains("fixture-secret"))
        val startTls = notifier.smtpProperties(config.copy(ssl = false, port = 587))
        assertEquals("true", startTls["mail.smtp.starttls.required"])
        assertEquals("true", startTls["mail.smtp.starttls.enable"])
    }

    @Test
    fun utf8EmailSurvivesMimeRoundTripAndContainsOnlySafeDiagnostics() {
        val notifier = SmtpHealthNotifier(config)
        val session = Session.getInstance(notifier.smtpProperties(config))
        val alert = HealthAlert("QG_BOT", Instant.parse("2026-10-04T00:00:00Z"), listOf(
            HealthCheckStatus("qq-api", "QQ 连接与鉴权", false, 2, "QQ API 错误码 11244"),
        ))
        val output = ByteArrayOutputStream()
        notifier.message(session, alert).writeTo(output)
        val parsed = MimeMessage(session, output.toByteArray().inputStream())
        assertEquals("[LoMu机器人] 心跳异常（QG_BOT）", parsed.subject)
        assertEquals("sender@example.com", parsed.from.single().toString())
        assertEquals("admin@example.com", parsed.getRecipients(Message.RecipientType.TO).single().toString())
        assertTrue(parsed.content.toString().contains("连续失败 2 次"))
        assertTrue(parsed.content.toString().contains("11244"))
        assertFalse(parsed.content.toString().contains("fixture-secret"))
        assertFalse(session.debug)
    }

    @Test
    fun oldConfigIsCompatibleAndSecretIsMaskedInDebugAndAdminViews() {
        val old = Json.decodeFromString<ConfigFile.BotConfig>("""{"port":8080}""")
        assertFalse(old.heartbeat.enabled)
        assertEquals(465, old.heartbeat.smtp.port)
        val configured = HeartbeatConfig(enabled = true, smtp = config)
        assertFalse(configured.toString().contains("fixture-secret"))
        val masked = Json.encodeToString(configured.copy(smtp = config.masked()))
        assertFalse(masked.contains("fixture-secret"))
        assertTrue(masked.contains("********"))
        assertEquals("", SmtpConfig().masked().password)
        val view = AdminConfigService(DatabaseManager(DatabaseBackend.SQLITE)) {
            ConfigFile.BotConfig(heartbeat = configured)
        }.view()
        assertEquals("********", view.heartbeat.smtp.password)
        assertFalse(Json.encodeToString(view).contains("fixture-secret"))
        assertEquals("fixture-secret", configured.smtp.password)
    }

    @Test
    fun blankCredentialsAreRejectedBeforeNetworkUse() {
        val notifier = SmtpHealthNotifier(SmtpConfig())
        assertFailsWith<IllegalArgumentException> {
            notifier.message(Session.getInstance(java.util.Properties()), HealthAlert("QQ", Instant.now(), emptyList()))
        }
    }
}
