package cn.luorenmu.qqbot.health

import cn.luorenmu.health.HealthCheck
import cn.luorenmu.health.HealthResult
import kotlinx.coroutines.CancellationException
import love.forte.simbot.application.Application
import love.forte.simbot.component.qguild.firstQQGuildBotManager
import love.forte.simbot.qguild.QQGuildApiException
import love.forte.simbot.qguild.api.user.GetBotInfoApi

fun qqHealthCheck(application: Application): HealthCheck = HealthCheck("qq-api", "QQ 连接与鉴权") {
    val bot = application.botManagers.firstQQGuildBotManager().all().firstOrNull()
    when {
        bot == null -> HealthResult(false, "QQ 机器人未注册")
        !bot.isActive -> HealthResult(false, "QQ 机器人未运行")
        else -> checkQQAuthentication { bot.executeData(GetBotInfoApi) }
    }
}

// 必须走当前 Simbot Bot 的缓存 token，独立申请新 token 会漏报缓存过期的问题。
internal suspend fun checkQQAuthentication(request: suspend () -> Any): HealthResult = try {
    request()
    HealthResult(true)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: QQGuildApiException) {
    // SDK 4.5.0 的三参数构造器会丢失 info，只在 message 中保留 ErrInfo。
    // 仅提取其数字错误码，绝不把原始响应或异常正文写入邮件/日志。
    val code = error.info?.code ?: legacyErrorCode.find(error.message.orEmpty())
        ?.groupValues?.get(1)?.toIntOrNull() ?: error.value
    HealthResult(false, "QQ API 鉴权/请求失败，错误码 $code")
}

private val legacyErrorCode = Regex("response info: ErrInfo\\(code=(\\d{1,9}),")
