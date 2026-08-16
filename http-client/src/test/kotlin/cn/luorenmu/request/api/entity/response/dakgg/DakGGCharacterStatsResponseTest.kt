package cn.luorenmu.request.api.entity.response.dakgg

import cn.luorenmu.request.entity.module.MatchingMode
import cn.luorenmu.request.entity.module.DakGGTeamMode
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class DakGGCharacterStatsResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `missing snapshot decodes as unavailable statistics`() {
        val response = json.decodeFromString<DakGGCharacterStatsResponse>(
            """{"error":{"status":404,"message":"characterStatRow"}}"""
        )

        assertNull(response.characterStatSnapshot)
    }

    @Test
    fun `cobalt matching and team modes use API enum casing`() {
        assertEquals("COBALT", MatchingMode.Cobalt.dakGGMode)
        assertEquals("COBALT", DakGGTeamMode.Cobalt.value)
    }
}
