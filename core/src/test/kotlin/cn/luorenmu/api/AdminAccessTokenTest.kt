package cn.luorenmu.api

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *
 * @author LoMu
 * Date 2026/8/16 14:11
 */
class AdminAccessTokenTest {
    @Test
    fun `regenerate creates url safe token and invalidates previous token`() {
        val previous = AdminAccessToken.regenerate()
        val current = AdminAccessToken.regenerate()

        assertTrue(current.matches(Regex("[A-Za-z0-9]{10}")))
        assertTrue(AdminAccessToken.matches(current))
        assertFalse(AdminAccessToken.matches(previous))
        assertFalse(AdminAccessToken.matches(null))
    }
}
