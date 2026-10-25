package cn.luorenmu.plugins.equipment

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Plugin-local endpoint so a plugin-only deployment does not require a new host http-client jar. */
internal object EquipmentStatsClient {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var cache: Pair<Long, EquipmentItemStatsResponse>? = null

    suspend fun fetch(): EquipmentItemStatsResponse {
        val now = System.currentTimeMillis()
        cache?.takeIf { now - it.first < 3_600_000 }?.let { return it.second }
        return withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder(URI("https://er.dakgg.io/api/v1/item-stats?dt=7"))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Mozilla/5.0")
                .GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() == 200) { "DAK.GG item stats returned HTTP ${response.statusCode()}" }
            json.decodeFromString<EquipmentItemStatsResponse>(response.body()).also { cache = now to it }
        }
    }
}

@Serializable
internal data class EquipmentItemStatsResponse(
    val meta: Meta = Meta(),
    val itemStatSnapshot: Snapshot = Snapshot(),
) {
    @Serializable data class Meta(val updatedAt: Long = 0)
    @Serializable data class Snapshot(val itemStats: List<ItemStat> = emptyList())
    @Serializable data class ItemStat(
        val key: Long = 0,
        val characterWeaponStats: List<CharacterWeaponStat> = emptyList(),
    )
    @Serializable data class CharacterWeaponStat(
        val key: List<Long> = emptyList(),
        val count: Long = 0,
        val count2: Long = 0,
        val win: Long = 0,
    )
}
