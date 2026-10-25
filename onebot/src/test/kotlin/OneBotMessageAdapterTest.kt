package cn.luorenmu.onebot

import love.forte.simbot.component.qguild.message.QGMarkdown
import love.forte.simbot.component.qguild.message.QGKeyboards
import love.forte.simbot.message.Messages
import love.forte.simbot.qguild.model.MessageKeyboard
import love.forte.simbot.message.PlainText
import love.forte.simbot.message.toText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * @author LoMu
 * Date 2026/8/16
 */
class OneBotMessageAdapterTest {
    @Test
    fun `markdown with QQ buttons falls back to plain text on OneBot`() {
        val message = Messages.of(QGMarkdown.create("## 玩家别名\n`/玩家别名 list`"),
            QGKeyboards.create(MessageKeyboard.builder("alias-list").renderData("列表", "列表", 1)
                .action(type = 2, permission = MessageKeyboard.ActionPermission.AllAccessible,
                    data = "/玩家别名 list", enter = true, reply = false).build()))
        val result = message.toOneBotCompatibleMessage() as Messages
        assertEquals(1, result.size)
        assertEquals("玩家别名\n/玩家别名 list", (result.single() as PlainText).text)
    }

    @Test
    fun `markdown becomes plain text while preserving line breaks`() {
        val markdown = QGMarkdown.create(
            "## 可用兑换码\n\n1. **测试活动**\n兑换码: `LOMU2026`\n> 今晚截止"
        )

        val result = markdown.toOneBotCompatibleMessage()

        assertEquals(
            "可用兑换码\n\n1. 测试活动\n兑换码: LOMU2026\n今晚截止",
            (result as PlainText).text,
        )
    }

    @Test
    fun `non markdown message remains unchanged`() {
        val text = "第一行\n第二行".toText()

        assertSame(text, text.toOneBotCompatibleMessage())
    }
}
