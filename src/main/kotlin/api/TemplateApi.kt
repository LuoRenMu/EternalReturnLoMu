package cn.luorenmu.api

import cn.luorenmu.request.entity.module.DakGGServerName
import cn.luorenmu.service.TemplateService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 *
 * @author LoMu
 * Date 2025/11/1 23:30
 */

private val log = KotlinLogging.logger { }
fun Route.templateRouting() {
    val api: TemplateService by inject()


    route("/template") {
        get("/tierStatisticsNumber") {
            api.run {
                val string = call.parameters["serverName"]
                val server = DakGGServerName.convert(string)
                call.tierStatisticsNumber(server)
            }
        }

        get("/searchPlayer/{nickname}"){
            val nickname = call.parameters["nickname"].toString()
            api.run {
                call.searchPlayer(nickname)
            }
        }
    }
}