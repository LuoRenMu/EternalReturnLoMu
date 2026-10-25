package cn.luorenmu.plugins.grouppet

import java.time.Duration
import java.time.Instant

internal class PetPlayerNotFoundException : RuntimeException("玩家不存在")

internal data class PetMatch(val id: Long, val startedAt: Long?, val seasonId: Long)
internal data class PetSnapshot(val playerId: Long, val nickname: String, val latest: PetMatch?)
internal fun interface GroupPetSource {
    suspend fun latest(nickname: String): PetSnapshot
    suspend fun forSubscription(nickname: String): PetSnapshot = latest(nickname)
}

internal data class PetSubscription(
    val groupId: String, val playerId: Long, val nickname: String, val requesterId: String,
    val lastMatchId: Long?, val lastMatchTime: Long?, val nextCheckAt: Long,
    val failures: Int = 0, val lastError: String? = null,
    val expiresAt: Long = 0,
)

internal object PetPollingPolicy {
    val subscriptionDuration: Duration = Duration.ofDays(7)
    fun interval(lastMatchTime: Long?, now: Long): Duration {
        val age = lastMatchTime?.let { Duration.ofMillis((now - it).coerceAtLeast(0)) }
            ?: return Duration.ofHours(6)
        return when {
            age < Duration.ofHours(2) -> Duration.ofMinutes(2)
            age < Duration.ofDays(1) -> Duration.ofMinutes(10)
            age < Duration.ofDays(7) -> Duration.ofMinutes(30)
            age < Duration.ofDays(30) -> Duration.ofHours(2)
            else -> Duration.ofHours(6)
        }
    }

    fun retry(failures: Int): Duration = Duration.ofMinutes((5L shl (failures - 1).coerceIn(0, 7)).coerceAtMost(360))
    fun label(lastMatchTime: Long?, now: Long): String {
        val minutes = interval(lastMatchTime, now).toMinutes()
        return if (minutes >= 60) "${minutes / 60} 小时" else "$minutes 分钟"
    }
}
