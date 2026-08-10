package cn.luorenmu.nutdraw.render

import java.net.URI
import java.nio.file.Files

class FileImageSource : ImageSource {
    override fun load(source: String): ByteArray? {
        val uri = runCatching { URI(source) }.getOrNull() ?: return null
        if (uri.scheme != "file") return null
        val path = runCatching { java.nio.file.Path.of(uri) }.getOrNull() ?: return null
        return path.takeIf(Files::isRegularFile)?.let(Files::readAllBytes)
    }
}
