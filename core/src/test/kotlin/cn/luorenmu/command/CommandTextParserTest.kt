package cn.luorenmu.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class CommandTextParserTest {
    private val commands = linkedMapOf(
        "查询" to true,
        "查询玩家" to true,
        "search" to true,
        "帮助" to false,
    )

    @Test
    fun slashIsOptionalAndFirstArgumentMayBeAttached() {
        val inputs = listOf(
            "/查询玩家 神圣审判",
            "查询玩家 神圣审判",
            "/查询玩家神圣审判",
            "查询玩家神圣审判",
        )

        inputs.forEach { input ->
            val found = CommandTextParser.find(input, commands) { it }
            assertEquals(true, found?.value)
            assertEquals("神圣审判", found?.arguments)
        }
    }

    @Test
    fun longestAliasWinsWhenArgumentsAreAttached() {
        val found = CommandTextParser.find("查询玩家神圣审判", commands) { it }
        assertEquals("神圣审判", found?.arguments)
    }

    @Test
    fun noArgumentCommandDoesNotConsumeAttachedText() {
        assertNull(CommandTextParser.find("帮助其他", commands) { it })
        assertEquals("", CommandTextParser.find("帮助", commands) { it }?.arguments)
    }

    @Test
    fun parsesAttachedFirstArgumentAndRemainingArguments() {
        val parsed = CommandTextParser.parseArguments(
            "<nickname> <mode>",
            "神圣审判 排位",
        )
        assertEquals(mapOf("nickname" to "神圣审判", "mode" to "排位"), parsed)
    }

    @Test
    fun everyAliasCanInvokeTheSameCommand() {
        listOf("search神圣审判", "查询玩家神圣审判").forEach { input ->
            assertEquals(
                "神圣审判",
                CommandTextParser.find(input, commands) { it }?.arguments,
            )
        }
    }
}
