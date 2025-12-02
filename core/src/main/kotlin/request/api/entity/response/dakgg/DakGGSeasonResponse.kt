package cn.luorenmu.request.api.entity.response.dakgg

/**
 *
 * @author LoMu
 * Date 2025/10/31 23:19
 */
import kotlinx.serialization.Serializable

@Serializable
data class DakGGSeasonResponse(
    val seasons: List<Season>,
) {
    fun getCurrentSeason(): DakGGSeasonResponse.Season {
        return seasons.first { it.isCurrent }
    }

    fun getSeasonById(id: Int): DakGGSeasonResponse.Season {
        return seasons.first { it.id == id }
    }

    fun getLatestSeason(): DakGGSeasonResponse.Season? {
        return seasons.maxByOrNull { it.id }
    }


    @Serializable
    data class Season(
        val id: Int,
        val key: String,
        val name: String,
        var isCurrent: Boolean = false,
    )
}


