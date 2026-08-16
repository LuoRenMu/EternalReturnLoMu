package cn.luorenmu.nutdraw

import cn.luorenmu.nutdraw.render.FontFallbackConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class FontFallbackConfigTest {
    @Test
    fun `equivalent fallback lists have value equality`() {
        val first = FontFallbackConfig(
            families = listOf("Noto Sans CJK SC", null),
            languages = listOf("zh-CN", "en-US"),
        )
        val second = FontFallbackConfig(
            families = listOf("Noto Sans CJK SC", null),
            languages = listOf("zh-CN", "en-US"),
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }
}
