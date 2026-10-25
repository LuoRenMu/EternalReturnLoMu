package cn.luorenmu.plugins.grouppet

import cn.luorenmu.request.api.entity.response.dakgg.DakGGMatchesResponse
import cn.luorenmu.request.api.entity.response.dakgg.DakGGSyncResponse
import cn.luorenmu.request.api.entity.response.game.BattleUserGamesResponse
import cn.luorenmu.request.api.impl.EternalReturnDakGGApi
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

internal class DakGroupPetSource : GroupPetSource {
    override suspend fun latest(nickname: String): PetSnapshot {
        // Await upstream synchronization and bypass the local match cache on every scheduled check.
        val synced = sync(nickname)
        val canonicalName = checkNotNull(synced.playerName)
        val playerId = subscriptionKey(canonicalName)
        val matchesResponse = EternalReturnDakGGApi.Game.GetGame(canonicalName).refresh()
        check(matchesResponse.status.isSuccess()) { "PlayerMatchesUnavailable" }
        return snapshot(playerId, canonicalName, matchesResponse.body<DakGGMatchesResponse>().matches, verifyIdentity = false)
    }

    private suspend fun sync(nickname: String): DakGGSyncResponse {
        repeat(3) { attempt ->
            val response = EternalReturnDakGGApi.User.Sync(nickname).refresh()
            check(response.status.isSuccess()) { "PlayerSyncUnavailable" }
            val synced = response.body<DakGGSyncResponse>()
            if (synced.isRateLimited() && attempt < 2) {
                kotlinx.coroutines.delay((synced.retryAfter?.toLong() ?: 1000).coerceIn(1000, 30_000))
            } else {
                validateSync(synced)
                return synced
            }
        }
        error("PlayerSyncRateLimited")
    }

    companion object {
        // DAK no longer exposes account IDs; match userNum=1 is an anonymized placeholder.
        // Persist a deterministic name-scoped subscription key, never that placeholder.
        internal fun subscriptionKey(name: String): Long {
            val bytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(name.trim().lowercase(java.util.Locale.ROOT).toByteArray(Charsets.UTF_8))
            return (java.nio.ByteBuffer.wrap(bytes).long and Long.MAX_VALUE).coerceAtLeast(1)
        }

        internal fun validateSync(synced: DakGGSyncResponse) {
            check(!synced.isRateLimited()) { "PlayerSyncRateLimited" }
            if (synced.isNotFound() && synced.playerName.isNullOrBlank()) throw PetPlayerNotFoundException()
            check(!synced.playerName.isNullOrBlank() && !synced.isNotFound()) { "PlayerSyncUnavailable" }
        }

        internal fun snapshot(playerId: Long, nickname: String, matches: List<BattleUserGamesResponse.UserGame>, verifyIdentity: Boolean = true): PetSnapshot {
            require(playerId > 0 && nickname.isNotBlank())
            check(!verifyIdentity || matches.all { it.userNum == playerId }) { "PlayerIdentityMismatch" }
            val latest = matches.filter { it.gameId > 0 }.maxByOrNull { it.gameId }
            val match = latest?.let {
                val start = it.startDtm.takeIf(String::isNotBlank)?.let { value ->
                    val normalized = value.replace(Regex("([+-][0-9]{2})([0-9]{2})$"), "$1:$2")
                    runCatching { Instant.parse(normalized) }.getOrElse { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }.toEpochMilli()
                }
                PetMatch(it.gameId, start, if (it.matchingMode == 6) 0 else it.seasonId)
            }
            return PetSnapshot(playerId, nickname, match)
        }
    }
}
