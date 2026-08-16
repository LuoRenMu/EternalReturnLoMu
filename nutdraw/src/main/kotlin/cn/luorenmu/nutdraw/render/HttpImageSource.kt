package cn.luorenmu.nutdraw.render

import java.net.URI

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class HttpImageSource : ImageSource {
    override fun load(source: String): ByteArray? {
        if (!source.startsWith("http://") && !source.startsWith("https://")) return null
        return URI(source).toURL().openStream().use { it.readBytes() }
    }
}
