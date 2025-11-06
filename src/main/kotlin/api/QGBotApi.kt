package cn.luorenmu.api

import cn.luorenmu.service.QGBotService
import cn.luorenmu.service.ResourcesService
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import kotlin.getValue

/**
 *
 * @author LoMu
 * Date 2025/10/22 23:43
 */

fun Route.qqBotRouting() {
    val api: QGBotService by inject()
    route("/callback/qq") {
        post("/{appId}") {
            api.run { call.registerQBot() }
        }
    }
}