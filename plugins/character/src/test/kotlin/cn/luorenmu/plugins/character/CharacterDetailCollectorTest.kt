package cn.luorenmu.plugins.character

import cn.luorenmu.request.api.entity.response.dakgg.CharacterAnalysisResponse
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class CharacterDetailCollectorTest {
    @Test
    fun `collects five most selected items for every equipment slot`() {
        val slots = listOf("武器", "胸甲", "头部", "手臂", "腿部")
        val builds = (0 until 6).map { variant ->
            CharacterAnalysisResponse.ItemBuildStat(
                key = slots.indices.map { slotIndex -> (slotIndex * 100 + variant).toLong() },
                count = (6 - variant).toLong(),
                win = (3 - variant).coerceAtLeast(0).toLong(),
            )
        }

        val selections = topEquipmentCounts(builds, slots, limitPerSlot = 5)

        assertEquals(25, selections.size)
        assertEquals(3, selections.first().win)
        slots.forEachIndexed { slotIndex, slot ->
            assertEquals(
                (0 until 5).map { variant -> (slotIndex * 100 + variant).toLong() },
                selections.filter { it.slot == slot }.map { it.itemId },
            )
        }
    }
}
