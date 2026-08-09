package cn.luorenmu.command

import cn.luorenmu.common.annotation.BotCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RedemptionCodeCommand 元数据测试（自包含，无需 Koin/网络/数据库）。
 */
class RedemptionCodeCommandTest {

    private val cmd = RedemptionCodeCommand()

    @Test
    fun annotationIsCorrect() {
        val anno = cmd::class.java.getAnnotation(BotCommand::class.java)
        assertNotNull(anno)
        assertEquals("兑换码", anno.alias)
        assertEquals("兑换码", anno.name)
        assertEquals("<limit>", anno.value)
    }

    @Test
    fun optionalsAreComplete() {
        assertEquals(1, cmd.optionals.size)
        assertEquals("limit", cmd.optionals[0].name)
        assertFalse(cmd.optionals[0].required)
        assertEquals("/兑换码", cmd.example)
    }

    @Test
    fun descriptionIsComplete() {
        assertTrue(cmd.description.contains("兑换码"))
    }
}
