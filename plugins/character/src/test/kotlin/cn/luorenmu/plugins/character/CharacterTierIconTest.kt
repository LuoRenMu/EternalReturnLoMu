package cn.luorenmu.plugins.character

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class CharacterTierIconTest {
    @Test
    fun `maps supported character tiers to dakgg svg`() {
        listOf("S", "A", "B", "C", "D").forEach { grade ->
            assertEquals(
                "/resources/images/character/tier/character-tier-$grade.svg",
                characterTierIconUrl(grade.lowercase()),
            )
        }
        assertEquals(null, characterTierIconUrl(""))
    }
}
