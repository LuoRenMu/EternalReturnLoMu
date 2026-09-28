package cn.luorenmu.common.util

import java.nio.file.Path

/**
 * Shared image integrity store for startup scans and on-demand downloads.
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
object ResourceCheckUtil {
    private val store by lazy {
        ImageIntegrityStore(
            PathUtils.resourcesPathResolve(),
            PathUtils.pathResolve(paths = arrayOf("data", "image-integrity.properties")),
        )
    }

    fun checkExistingImages(): ImageIntegrityStore.ScanResult = store.scanExistingImages()
    fun checkResource(resource: Path): Boolean = store.check(resource)
    fun markResourceInvalid(resource: Path) = store.invalidate(resource)
    fun markResourceValid(resource: Path) { store.check(resource) }
    fun removeResource(resource: Path) = store.invalidate(resource)
    fun getCacheSize(): Int = store.recordCount()
    fun clearCache() = store.clearMemory()
    fun installVerified(resource: Path, bytes: ByteArray) = store.installVerified(resource, bytes)
}
