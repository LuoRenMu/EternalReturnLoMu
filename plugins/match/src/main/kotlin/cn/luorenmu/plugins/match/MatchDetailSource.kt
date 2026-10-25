package cn.luorenmu.plugins.match

import cn.luorenmu.exception.MessageReplyException
import cn.luorenmu.request.ApiKeyConfig
import cn.luorenmu.request.api.PakeApi
import cn.luorenmu.request.api.entity.module.CacheTime
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import cn.luorenmu.request.api.impl.EternalReturnOpenApi
import cn.luorenmu.service.ResourcesDownloadService
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import org.koin.java.KoinJavaComponent.inject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal object MatchDetailSource {
    private val resources: ResourcesDownloadService by inject(ResourcesDownloadService::class.java)

    suspend fun query(id: Long, nickname: String?, season: Long?, downloader: ResourcesDownloadService = resources): MatchDetailData = coroutineScope {
        val snapshot = if (nickname != null && season != null) {
            MatchDetailMapper.decode(DakMatchApi(id, nickname, season).execute(), id)
        } else {
            val key = ApiKeyConfig.apiKeyMap.entries.firstOrNull { it.key.equals("x-api-key", true) }?.value
            if (key.isNullOrBlank() || key.any { it.code > 127 })
                throw MessageReplyException("请先查询玩家，再点击最近对局按钮；直接按 ID 查询需要配置官方 API Key")
            val response = EternalReturnOpenApi.Game.GetGameByGameId(id).execute()
            if (response.code != 200) throw MessageReplyException("该对局暂不可查询，请稍后重试")
            MatchDetailMapper.validate(response.userGames, id)
            MatchSnapshot(response.userGames)
        }
        val characterJob = async { EternalReturnDakGGApi.Data.GetCharacters.execute() }
        val tierJob = async { EternalReturnDakGGApi.Data.GetTiers.execute() }
        val characters = characterJob.await()
        val tiers = tierJob.await()
        MatchDetailAssets.download(snapshot, characters, tiers, downloader)
        MatchDetailMapper.assemble(snapshot, nickname, characters, tiers)
    }

    private class DakMatchApi(id: Long, nickname: String, season: Long) : PakeApi(
        url = "/v1/players/${URLEncoder.encode(nickname, StandardCharsets.UTF_8).replace("+", "%20")}/matches/$season/$id",
        cacheTime = CacheTime.HOUR,
    ) {
        override var baseUrl = "https://er.dakgg.io/api"
        init { headers["User-Agent"] = "Mozilla/5.0" }

        suspend fun execute(): JsonObject {
            val response = call()
            if (!response.status.isSuccess()) throw MessageReplyException("该对局暂不可查询，请稍后重试")
            return response.body<JsonObject>()
        }
    }
}
