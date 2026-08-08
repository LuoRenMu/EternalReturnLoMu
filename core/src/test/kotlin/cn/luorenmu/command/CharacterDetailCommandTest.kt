package cn.luorenmu.command

import cn.luorenmu.common.annotation.BotCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * CharacterDetailCommand 元数据测试（自包含，无需 Koin/网络/数据库）。
 */
class CharacterDetailCommandTest {

    private val cmd = CharacterDetailCommand()

    @Test
    fun annotationIsCorrect() {
        val anno = cmd::class.java.getAnnotation(BotCommand::class.java)
        assertNotNull(anno)
        assertEquals("角色详情", anno.alias)
        assertEquals("角色详情", anno.name)
        assertEquals("<character> <mode> <tier>", anno.value)
    }

    @Test
    fun optionalsAreComplete() {
        assertEquals(3, cmd.optionals.size)
        assertEquals("character", cmd.optionals[0].name)
        assertTrue(cmd.optionals[0].required)
        assertEquals("mode", cmd.optionals[1].name)
        assertFalse(cmd.optionals[1].required)
        assertEquals("tier", cmd.optionals[2].name)
        assertFalse(cmd.optionals[2].required)
        assertEquals("/角色详情 阿德拉 排位 灭钻", cmd.example)
    }

    @Test
    fun descriptionIsComplete() {
        assertTrue(cmd.description.contains("角色详情"))
    }
}
