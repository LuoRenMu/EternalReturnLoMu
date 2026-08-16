package cn.luorenmu.plugins.character

import cn.luorenmu.common.annotation.BotCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class CharacterStatsCommandTest {
    private val command = CharacterStatsCommand()

    @Test
    fun `command only accepts rank`() {
        val annotation = assertNotNull(command::class.java.getAnnotation(BotCommand::class.java))

        assertEquals("<rank>", annotation.value)
        assertEquals(listOf("rank"), command.optionals.map { it.name })
        assertFalse(command.optionals.single().required)
        assertEquals("/角色数据 灭钻", command.example)
    }
}
