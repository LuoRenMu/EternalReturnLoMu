package cn.luorenmu.command

import cn.luorenmu.common.annotation.BotCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class CommandAliasRegistryTest {
    @BotCommand(
        name = "查询玩家",
        alias = "search",
        value = "<nickname>",
        aliases = ["查询玩家", "查玩家"],
    )
    private class MultiAliasCommand

    @Test
    fun registersPrimaryAliasAndAllAdditionalAliases() {
        val command = checkNotNull(MultiAliasCommand::class.java.getAnnotation(BotCommand::class.java))
        val target = mutableMapOf<String, String>()
        CommandAliasRegistry.register(target, command, "player")
        assertEquals(
            mapOf("search" to "player", "查询玩家" to "player", "查玩家" to "player"),
            target,
        )
    }

    @Test
    fun rejectsAliasesAlreadyOwnedByAnotherCommand() {
        val command = checkNotNull(MultiAliasCommand::class.java.getAnnotation(BotCommand::class.java))
        val target = mutableMapOf("查询玩家" to "existing")
        assertFailsWith<IllegalArgumentException> {
            CommandAliasRegistry.register(target, command, "player")
        }
    }
}
