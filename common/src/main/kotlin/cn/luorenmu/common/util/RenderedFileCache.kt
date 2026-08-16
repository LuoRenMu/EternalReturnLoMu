package cn.luorenmu.common.util

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.HexFormat

/**
 * 复用指定有效期内的渲染文件，并合并同一路径的并发生成请求。
 *
 * @author LoMu
 * Date 2026/8/16
 */
object RenderedFileCache {
    val DEFAULT_MAX_AGE: Duration = Duration.ofHours(12)

    suspend fun getOrCreate(
        path: Path,
        maxAge: Duration = DEFAULT_MAX_AGE,
        producer: suspend (Path) -> Unit,
    ): Path {
        require(!maxAge.isZero && !maxAge.isNegative) { "maxAge 必须大于 0" }
        val normalizedPath = path.toAbsolutePath().normalize()
        return StringLockUtil.withKeyLock("rendered-file:$normalizedPath") {
            if (!isFresh(normalizedPath, maxAge)) {
                normalizedPath.parent?.let(Files::createDirectories)
                producer(normalizedPath)
                check(Files.isRegularFile(normalizedPath)) { "渲染文件未生成: $normalizedPath" }
            }
            normalizedPath
        }
    }

    fun cacheKey(vararg parts: String): String {
        val source = parts.joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(digest).take(CACHE_KEY_LENGTH)
    }

    private fun isFresh(path: Path, maxAge: Duration, now: Instant = Instant.now()): Boolean {
        if (!Files.isRegularFile(path)) return false
        return Files.getLastModifiedTime(path).toInstant().plus(maxAge).isAfter(now)
    }

    private const val CACHE_KEY_LENGTH = 24
}
