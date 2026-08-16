package cn.luorenmu.command.plugin

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *
 * @author LoMu
 * Date 2026/8/16
 */
class CoreCommandPluginVersionTest {
    @Test
    fun coreCommandPluginUsesCoreVersion() {
        assertEquals("3.0.0", CoreCommandPlugin().version)
    }
}
