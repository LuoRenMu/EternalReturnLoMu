package cn.luorenmu.nutdraw.render

import org.jetbrains.skia.Image
import org.jetbrains.skia.svg.SVGDOM

sealed interface LoadedImage : AutoCloseable {
    /** Raster images are borrowed from the renderer cache and are not closed per draw. */
    data class Raster(val image: Image) : LoadedImage { override fun close() = Unit }
    data class Vector(val source: String, val dom: SVGDOM) : LoadedImage { override fun close() = dom.close() }
}
