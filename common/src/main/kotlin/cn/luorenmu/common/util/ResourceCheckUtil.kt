package cn.luorenmu.common.util

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe cache for validated local resources.
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
object ResourceCheckUtil {
    private val fileMap = ConcurrentHashMap<Path, Byte>()

    fun checkResource(resource: Path): Boolean {
        val path = resource.normalized()
        fileMap[path]?.let { return it > 0 }
        val file = path.toFile()
        if (file.exists() && file.length() > 0) {
            fileMap[path] = 1
            return true
        }
        return false
    }

    fun markResourceInvalid(resource: Path) {
        fileMap[resource.normalized()] = 0
    }

    fun markResourceValid(resource: Path) {
        val path = resource.normalized()
        if (path.toFile().let { it.exists() && it.length() > 0 }) fileMap[path] = 1
    }

    fun removeResource(resource: Path) {
        fileMap.remove(resource.normalized())
    }

    fun getCacheSize(): Int = fileMap.size

    fun clearCache() = fileMap.clear()

    private fun Path.normalized(): Path = toAbsolutePath().normalize()
}
