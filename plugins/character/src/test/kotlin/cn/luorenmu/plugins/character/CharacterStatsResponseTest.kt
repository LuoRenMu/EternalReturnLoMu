package cn.luorenmu.plugins.character

import cn.luorenmu.exception.MessageReplyException
import cn.luorenmu.request.api.entity.response.dakgg.DakGGCharacterStatsResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class CharacterStatsResponseTest {
    @Test
    fun `missing statistics snapshot becomes friendly message`() {
        val error = assertFailsWith<MessageReplyException> {
            DakGGCharacterStatsResponse().requireSnapshot()
        }

        assertEquals("数据统计中..", error.message)
    }
}
