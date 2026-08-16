package cn.luorenmu.nutdraw.render

import java.util.concurrent.ConcurrentHashMap

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class ImageByteCache {
    private val entries = ConcurrentHashMap<String, ByteArray>()
    fun getOrLoad(source: String, loader: () -> ByteArray): ByteArray = entries.computeIfAbsent(source) { loader() }
}
