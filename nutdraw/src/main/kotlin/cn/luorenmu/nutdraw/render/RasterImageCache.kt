package cn.luorenmu.nutdraw.render

import org.jetbrains.skia.Image
import java.util.LinkedHashMap

/** Renderer-scoped LRU of immutable decoded raster images. */
class RasterImageCache(private val maxEntries: Int = 512) {
    private val entries = object : LinkedHashMap<String, Image>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Image>?): Boolean = size > maxEntries
    }

    @Synchronized
    fun getOrDecode(source: String, decode: () -> Image): Image = entries[source] ?: decode().also { entries[source] = it }
}
