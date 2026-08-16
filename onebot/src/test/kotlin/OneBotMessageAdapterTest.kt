package cn.luorenmu.onebot

import love.forte.simbot.component.qguild.message.QGMarkdown
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
