package cn.luorenmu.plugins.character

import cn.luorenmu.common.annotation.BotCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class CharacterDetailCommandTest {
    private val command = CharacterDetailCommand()

    @Test
    fun annotationIsComplete() {
        val annotation = command::class.java.getAnnotation(BotCommand::class.java)
        assertNotNull(annotation)
        assertTrue(annotation.name.isNotBlank())
        assertEquals(annotation.name, annotation.alias)
        assertEquals("<character> <mode> <tier>", annotation.value)
    }

    @Test
    fun optionalsAreComplete() {
        assertEquals(listOf("character", "mode", "tier"), command.optionals.map { it.name })
        assertTrue(command.optionals[0].required)
        assertFalse(command.optionals[1].required)
        assertFalse(command.optionals[2].required)
        val annotation = command::class.java.getAnnotation(BotCommand::class.java)
        assertTrue(command.example.startsWith("/${annotation.alias} "))
    }

    @Test
    fun descriptionIsComplete() {
        assertTrue(command.description.isNotBlank())
    }
}
