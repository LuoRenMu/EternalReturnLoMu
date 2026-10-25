package cn.luorenmu.plugins.player

import cn.luorenmu.command.entity.MessageSender
import kotlinx.coroutines.runBlocking
import love.forte.simbot.common.id.ID
import love.forte.simbot.common.id.StringID
import love.forte.simbot.component.qguild.message.QGKeyboards
import love.forte.simbot.component.qguild.message.QGMarkdown
import love.forte.simbot.message.Messages
import kotlin.test.*

class PlayerAliasButtonsTest {
    @Test fun `help and list replies have editable set delete and executable list buttons`() = runBlocking {
        val id = StringID::class.java.getMethod("valueOf", String::class.java).invoke(null, "alias-test") as ID
        val sender = MessageSender(id, "tester", id, "/玩家别名", "/玩家别名")
        val command = PlayerAliasCommand()
        val help = assertIs<Messages>(command.listen(sender, emptyMap())).toList()
        assertTrue(assertIs<QGMarkdown>(help.first()).markdown.content.orEmpty().contains("别名命令"))
        for (reply in listOf(help, assertIs<Messages>(command.withButtons("暂无别名")).toList())) {
            val buttons = assertIs<QGKeyboards>(reply[1]).keyboards.content.rows.flatMap { it.buttons }
            assertEquals(3, buttons.size)
            buttons.zip(listOf("set", "del", "list")).forEach { (button, action) ->
                assertEquals(2, button.action?.type)
                assertEquals(2, button.action?.permission?.type)
                assertEquals("/玩家别名 $action${if (action == "list") "" else " "}", button.action?.data)
                assertEquals(action == "list", button.action?.enter)
                assertEquals(false, button.action?.reply)
            }
        }
    }
}
