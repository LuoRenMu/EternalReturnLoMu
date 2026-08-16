package cn.luorenmu.api

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 *
 * @author LoMu
 * Date 2026/8/16 14:11
 */
internal object AdminAccessToken {
    const val COOKIE_NAME = "lomu-admin-access"
    const val HEADER_NAME = "X-Admin-Token"
    const val QUERY_NAME = "token"

    @Volatile
    private var current = ""

    fun configure(token: String) {
        val normalized = token.trim()
        require(normalized.isNotEmpty()) { "config.json 中的 adminToken 不能为空" }
        current = normalized
    }

    fun matches(candidate: String?): Boolean {
        val normalized = candidate?.trim().orEmpty()
        if (normalized.isEmpty() || current.isEmpty()) return false
        return MessageDigest.isEqual(
            current.toByteArray(StandardCharsets.UTF_8),
            normalized.toByteArray(StandardCharsets.UTF_8),
        )
    }
}
