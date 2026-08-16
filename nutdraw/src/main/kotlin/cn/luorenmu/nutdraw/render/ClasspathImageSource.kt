package cn.luorenmu.nutdraw.render

import java.net.URI

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class ClasspathImageSource(private val owner: Class<*> = ClasspathImageSource::class.java) : ImageSource {
    override fun load(source: String): ByteArray? {
        val path = runCatching { URI(source).path }.getOrNull() ?: source
        val classpath = path.takeIf { it.startsWith('/') } ?: "/$path"
        return owner.getResourceAsStream(classpath)?.use { it.readBytes() }
    }
}
