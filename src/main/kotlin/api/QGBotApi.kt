package cn.luorenmu.api

import cn.luorenmu.service.QGBotService
import io.ktor.server.application.*
import io.ktor.server.routing.*

/**
 *
 * @author LoMu
 * Date 2025/10/22 23:43
 */

fun Route.qqBotRouting(api: QGBotService = QGBotService()) {
    route("/callback/qq") {
        post("/{appId}") {
            api.run { call.registerQBot() }
        }
    }
}