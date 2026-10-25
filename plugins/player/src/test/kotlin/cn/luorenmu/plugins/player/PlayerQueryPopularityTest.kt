package cn.luorenmu.plugins.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerQueryPopularityTest {
    @Test
    fun `popularity aside appears only above fifty queries`() {
        val rate = "状态在线喵！"
        assertEquals(rate, playerRateWithQueryCount(rate, 0))
        assertEquals(rate, playerRateWithQueryCount(rate, 50))
        assertEquals("状态在线喵！ 该玩家已经被查询 51 次，这是群宠吗喵？",
            playerRateWithQueryCount(rate, 51))
        assertEquals("状态在线喵！ 该玩家已经被查询 122 次，这是群宠吗喵？",
            playerRateWithQueryCount(rate, 122))
    }
}
