package cn.luorenmu.service

import cn.luorenmu.request.api.EternalReturnOpenApiClient
import cn.luorenmu.request.entity.module.DakGGServerName
import cn.luorenmu.request.entity.module.MatchingMode
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.freemarker.*
import io.ktor.server.response.*
import org.koin.java.KoinJavaComponent.inject

/**
 *
 * @author LoMu
 * Date 2025/11/1 23:32
 */
class TemplateService {

    private val log = KotlinLogging.logger { }
    private val eternalReturnRenderService: EternalReturnRenderService by inject(
        EternalReturnRenderService::class.java
    )

    suspend fun ApplicationCall.tierStatisticsNumber(serverName: DakGGServerName) {
        val cutoffsAndTierNumber = eternalReturnRenderService.getCutoffsAndTierNumber(serverName)
        respond(
            FreeMarkerContent(
                "tier_statistics_number.ftl",
                cutoffsAndTierNumber,
                contentType = ContentType.Text.Html
                    .withCharset(Charsets.UTF_8)
            )
        )
    }

    suspend fun ApplicationCall.searchPlayer(nickname: String) {
        val user = EternalReturnOpenApiClient.getUserNumByUserNickName(nickname)
        val currentTimeMillis = System.currentTimeMillis()
        val eternalReturnRender =
            eternalReturnRenderService.getEternalReturnRender(user.user.userId, nickname, MatchingMode.Rank)
        val currentTimeMillis1 = System.currentTimeMillis()
        log.info { "获取数据耗时：${(currentTimeMillis1 - currentTimeMillis) / 1000}s" }
        respond(
            FreeMarkerContent(
                "search_player.ftl",
                eternalReturnRender,
                contentType = ContentType.Text.Html
                    .withCharset(Charsets.UTF_8)
            )
        )
    }

    suspend fun ApplicationCall.oldName(nickname: String) {
        val oldName = eternalReturnRenderService.oldName(nickname)
        respond(
            FreeMarkerContent(
                "old_name.ftl",
                oldName,
                contentType = ContentType.Text.Html
                    .withCharset(Charsets.UTF_8)
            ).template
        )
    }


}