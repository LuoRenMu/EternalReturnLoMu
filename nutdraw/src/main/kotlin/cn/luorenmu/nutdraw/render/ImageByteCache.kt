package cn.luorenmu.nutdraw.render

import java.util.concurrent.ConcurrentHashMap

class ImageByteCache {
    private val entries = ConcurrentHashMap<String, ByteArray>()
    fun getOrLoad(source: String, loader: () -> ByteArray): ByteArray = entries.computeIfAbsent(source) { loader() }
}
