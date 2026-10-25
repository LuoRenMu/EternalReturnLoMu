package cn.luorenmu.nutdraw.render

import java.net.URI

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class HttpImageSource(
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 10_000,
    private val maxBytes: Int = 8 * 1024 * 1024,
) : ImageSource {
    override fun load(source: String): ByteArray? {
        if (!source.startsWith("http://") && !source.startsWith("https://")) return null
        val connection = URI(source).toURL().openConnection().apply {
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
        }
        return connection.getInputStream().use { input ->
            input.readNBytes(maxBytes + 1).also { require(it.size <= maxBytes) { "Image exceeds $maxBytes bytes" } }
        }
    }
}
