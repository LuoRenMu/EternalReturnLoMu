package cn.luorenmu.request.api.entity.response.dakgg

import cn.luorenmu.request.api.entity.response.data.GameDataSeasonResponse
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

/**
 *
 * @author LoMu
 * Date 2025/11/1 00:17
 */
@Serializable
data class DakGGCurrentSeasonResponse(
    val id:Int,
    val type:String,
    val name:String,
){
    fun convert(): GameDataSeasonResponse {
        return GameDataSeasonResponse(
            this.id,
            this.name,
            LocalDateTime.MIN,
            LocalDateTime.MAX,
            1
        )
    }
}


