package cn.luorenmu.request.api.entity.response.dakgg

import kotlin.test.Test
import kotlin.test.assertEquals

class DakGGLeaderboardResponseTest {
    @Test
    fun `finds eternal and demigod when response contains additional cutoffs`() {
        val cutoffs = arrayListOf(
            DakGGLeaderboardResponse.Cutoffs(mmr = 100, tierType = 6),
            DakGGLeaderboardResponse.Cutoffs(mmr = 200, tierType = 7),
            DakGGLeaderboardResponse.Cutoffs(mmr = 300, tierType = 8),
        )
        val result = cutoffs.resolveCutoffs()
        assertEquals(300, result?.first?.mmr)
        assertEquals(200, result?.second?.mmr)
    }
}
