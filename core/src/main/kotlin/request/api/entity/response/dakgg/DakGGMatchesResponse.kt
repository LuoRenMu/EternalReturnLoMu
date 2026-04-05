package cn.luorenmu.request.api.entity.response.dakgg

import cn.luorenmu.request.api.entity.response.game.BattleUserGamesResponse
import kotlinx.serialization.Serializable

/**
 *
 * @author LoMu
 * Date 2026/4/5 16:59
 */

@Serializable
data class DakGGMatchesResponse(
    val matches: List<BattleUserGamesResponse.UserGame>,
)
