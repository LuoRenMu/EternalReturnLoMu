package cn.luorenmu.request.api.entity.response.dakgg

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNull

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class DakGGProfileResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `nullable player seasons decode successfully`() {
        val response = json.decodeFromString<DakGGProfileResponse>(
            """{"meta":{},"player":{},"playerSeasons":null}"""
        )

        assertNull(response.playerSeasons)
    }
}
