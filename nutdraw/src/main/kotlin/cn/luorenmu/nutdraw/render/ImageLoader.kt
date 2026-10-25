package cn.luorenmu.nutdraw.render

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class ImageLoader(
    private val bytes: ImageByteLoader = ImageByteLoader(),
    private val cache: ImageByteCache = ImageByteCache(),
    private val decoder: ImageDecoder = ImageDecoder(),
) {
    private val downloadPermits = Semaphore(6)

    /** Fetch only bytes in parallel: native Skia decoding and drawing stay on the render thread. */
    internal suspend fun prefetch(sources: Collection<String>): Set<String> = coroutineScope {
        sources.distinct().map { source ->
            async(Dispatchers.IO) {
                downloadPermits.withPermit {
                    try {
                        cache.getOrLoad(source) { bytes.load(source) }
                        null
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        source
                    }
                }
            }
        }.awaitAll().filterNotNull().toSet()
    }

    fun load(source: String?): LoadedImage? = source?.takeIf(String::isNotBlank)?.let { value ->
        runCatching { decoder.decode(value, cache.getOrLoad(value) { bytes.load(value) }) }.getOrNull()
    }
}
