package cn.luorenmu.health

import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.util.Date
import java.util.Properties

class SmtpHealthNotifier(private val config: SmtpConfig) : HealthNotifier {
    override suspend fun send(alert: HealthAlert): Unit = runInterruptible(Dispatchers.IO) {
        val session = Session.getInstance(smtpProperties(config)).apply { debug = false }
        val message = message(session, alert)
        val transport = session.getTransport("smtp")
        try {
            transport.connect(config.host, config.port, config.user, config.password)
            transport.sendMessage(message, message.allRecipients)
        } finally {
            transport.close()
        }
    }

    internal fun message(session: Session, alert: HealthAlert): MimeMessage {
        require(config.provider == "smtp" && config.host.isNotBlank()) { "SMTP provider/host 无效" }
        require(config.user.isNotBlank() && config.password.isNotBlank() && config.to.isNotBlank()) {
            "SMTP 邮箱、授权码和收件人不能为空"
        }
        val from = InternetAddress(config.user, true).also { it.validate() }
        val to = InternetAddress(config.to, true).also { it.validate() }
        return MimeMessage(session).apply {
            setFrom(from)
            setRecipient(Message.RecipientType.TO, to)
            setSubject("[LoMu机器人] 心跳异常（${alert.adapter}）", "UTF-8")
            sentDate = Date.from(alert.time)
            setText(buildString {
                appendLine("机器人心跳检测发现异常")
                appendLine("适配器：${alert.adapter}")
                appendLine("检测时间（UTC）：${alert.time}")
                alert.failures.forEach {
                    appendLine("${it.title}：${it.detail}（连续失败 ${it.consecutiveFailures} 次）")
                }
                appendLine("请检查机器人服务、网络和鉴权；本邮件不包含凭据或原始请求。")
            }, "UTF-8")
            saveChanges()
        }
    }

    internal fun smtpProperties(config: SmtpConfig): Properties {
        require(config.port in 1..65535) { "SMTP 端口无效" }
        return Properties().apply {
            setProperty("mail.smtp.auth", "true")
            setProperty("mail.smtp.connectiontimeout", "10000")
            setProperty("mail.smtp.timeout", "10000")
            setProperty("mail.smtp.writetimeout", "10000")
            setProperty("mail.smtp.ssl.enable", config.ssl.toString())
            setProperty("mail.smtp.ssl.checkserveridentity", "true")
            if (!config.ssl) {
                setProperty("mail.smtp.starttls.enable", "true")
                setProperty("mail.smtp.starttls.required", "true")
            }
        }
    }
}
