package cn.luorenmu.nutdraw.render

import cn.luorenmu.common.util.PathUtils
import java.net.URI
import java.nio.file.Files

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class ResourceImageSource : ImageSource {
    override fun load(source: String): ByteArray? {
        val path = runCatching { URI(source).path }.getOrNull() ?: source
        if (!path.startsWith("/resources/")) return null
        val local = PathUtils.resourcesPathResolve(path.removePrefix("/resources/"))
        return local.takeIf(Files::isRegularFile)?.let(Files::readAllBytes)
    }
}
