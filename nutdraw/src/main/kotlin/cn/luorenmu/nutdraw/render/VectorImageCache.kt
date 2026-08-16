package cn.luorenmu.nutdraw.render

import cn.luorenmu.nutdraw.css.ObjectFit
import org.jetbrains.skia.Image
import java.util.LinkedHashMap

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
data class VectorImageKey(val source: String, val width: Int, val height: Int, val fit: ObjectFit)

/** Caches rasterised SVG variants because templates repeatedly draw a few SVGs at fixed sizes. */
class VectorImageCache(private val maxEntries: Int = 256) {
    private val entries = object : LinkedHashMap<VectorImageKey, Image>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<VectorImageKey, Image>?): Boolean = size > maxEntries
    }

    @Synchronized
    fun getOrRender(key: VectorImageKey, render: () -> Image): Image = entries[key] ?: render().also { entries[key] = it }
}
