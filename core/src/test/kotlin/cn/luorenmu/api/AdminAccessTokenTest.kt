package cn.luorenmu.api

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *
 * @author LoMu
 * Date 2026/8/16 14:11
 */
class AdminAccessTokenTest {
    @Test
    fun `configure uses fixed token and replaces previous token`() {
        AdminAccessToken.configure("previous")
        AdminAccessToken.configure("  fixed-token  ")

        assertTrue(AdminAccessToken.matches("fixed-token"))
        assertTrue(AdminAccessToken.matches("  fixed-token  "))
        assertFalse(AdminAccessToken.matches("previous"))
        assertFalse(AdminAccessToken.matches(null))
    }

    @Test
    fun `configure rejects blank token`() {
        assertFailsWith<IllegalArgumentException> { AdminAccessToken.configure("   ") }
    }
}
