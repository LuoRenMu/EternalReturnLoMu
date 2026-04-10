package cn.luorenmu.qqbot.service

import cn.luorenmu.qqbot.simbotApplication
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import love.forte.simbot.component.qguild.bot.emitEvent
import love.forte.simbot.component.qguild.filterIsQQGuildBotManagers
import love.forte.simbot.qguild.stdlib.Ed25519SignatureVerification
import love.forte.simbot.qguild.stdlib.EmitResult

/**
 *
 * @author LoMu
 * Date 2025/10/23 13:00
 */
class QGBotService {

    companion object {
        private const val SIGNATURE_HEAD = "X-Signature-Ed25519"
        private const val TIMESTAMP_HEAD = "X-Signature-Timestamp"
    }

    suspend fun ApplicationCall.registerQBot() {
        val appId = parameters["appId"]

        // 寻找指定 `appId` 的 QGBot
        val targetBot = simbotApplication.botManagers
            .filterIsQQGuildBotManagers()
            .firstNotNullOfOrNull {
                it.all().firstOrNull { bot ->
                    bot.source.ticket.appId == appId
                }
            }

        // 如果找不到，响应 404 异常
        if (targetBot == null) {
            respond(HttpStatusCode.Companion.NotFound)
            return
        }

        // 准备参数
        val signature = request.header(SIGNATURE_HEAD)
            ?: run {
                respond(
                    HttpStatusCode.Companion.BadRequest,
                    "Required header $SIGNATURE_HEAD is missing"
                )
                return
            }

        val timestamp = request.header(TIMESTAMP_HEAD)
            ?: run {
                respond(
                    HttpStatusCode.Companion.BadRequest,
                    "Required header $TIMESTAMP_HEAD is missing"
                )
                return
            }

        val payload = receiveText()

        val result = targetBot.emitEvent(
            payload,
        ) {
            // 配置 ed25519SignatureVerification, 即代表进行签名校验
            ed25519SignatureVerification = Ed25519SignatureVerification(
                signature,
                timestamp
            )
        }

        val respond: String? = when (result) {
            is EmitResult.Verified ->
                Json.encodeToString(result.verified)

            else -> null
        }

        // 响应成功结果
        respondText(
            respond ?: "{}",
            ContentType.Application.Json
        )
    }
}