package cn.luorenmu.api

import java.security.MessageDigest
import java.security.SecureRandom

internal object AdminAccessToken {
    const val COOKIE_NAME = "lomu-admin-access"
    const val HEADER_NAME = "X-Admin-Token"
    const val QUERY_NAME = "token"

    private val secureRandom = SecureRandom()

    @Volatile
    private var current = ""

    fun regenerate(): String {
        return buildString(TOKEN_LENGTH) {
            repeat(TOKEN_LENGTH) {
                append(TOKEN_ALPHABET[secureRandom.nextInt(TOKEN_ALPHABET.length)])
            }
        }.also { current = it }
    }

    fun matches(candidate: String?): Boolean {
        val normalized = candidate?.trim().orEmpty()
        if (normalized.isEmpty() || current.isEmpty()) return false
        return MessageDigest.isEqual(current.toByteArray(), normalized.toByteArray())
    }

    private const val TOKEN_LENGTH = 10
    private const val TOKEN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
}
