package cn.luorenmu.health

import kotlinx.serialization.Serializable

@Serializable
data class HeartbeatConfig(
    val enabled: Boolean = false,
    val intervalSeconds: Int = 60,
    val startupGraceSeconds: Int = 30,
    val timeoutSeconds: Int = 10,
    val failureThreshold: Int = 2,
    val alertCooldownSeconds: Int = 1800,
    val notificationRetrySeconds: Int = 300,
    val smtp: SmtpConfig = SmtpConfig(),
)

@Serializable
data class SmtpConfig(
    val provider: String = "smtp",
    val host: String = "smtp.qq.com",
    val port: Int = 465,
    val ssl: Boolean = true,
    val user: String = "",
    val password: String = "",
    val to: String = "",
) {
    // 配置经日志、调试器或父配置的 toString 输出时也不能泄漏授权码。
    override fun toString(): String =
        "SmtpConfig(provider=$provider, host=$host, port=$port, ssl=$ssl, user=$user, password=********, to=$to)"

    fun masked(): SmtpConfig = copy(password = if (password.isBlank()) "" else "********")
}
