package cn.luorenmu.plugins.character

import kotlin.test.Test
import kotlin.test.assertEquals

class CharacterDetailLabelsTest {
    @Test
    fun `translates roles and removes empty duplicate roles`() {
        assertEquals("战士 / 刺客 / 坦克 / 法师 / 射手 / 辅助", characterRoleLabels(
            listOf(" Warrior ", "Assassin", "Tank", "Mage", "Marksman", "Support", "None", "", "fighter")))
        assertEquals("坦克 / 射手 / 辅助", characterRoleLabels(listOf("Tanker", "ADC", "Supporter")))
        assertEquals("新职能", characterRoleLabels(listOf("新职能")))
    }

    @Test
    fun `missing or invalid rank is not displayed as zero`() {
        assertEquals("#3 / 115", characterRankingLabel(3, 115))
        assertEquals("暂无排名", characterRankingLabel(0, 0))
        assertEquals("暂无排名", characterRankingLabel(3, 0))
    }
}
