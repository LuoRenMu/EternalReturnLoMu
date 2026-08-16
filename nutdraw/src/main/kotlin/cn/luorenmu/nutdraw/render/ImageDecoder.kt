package cn.luorenmu.nutdraw.render

import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.svg.SVGDOM

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class ImageDecoder(
    private val formats: ImageFormatDetector = ImageFormatDetector(),
    private val rasters: RasterImageCache = RasterImageCache(),
) {
    fun decode(source: String, bytes: ByteArray): LoadedImage =
        if (formats.isSvg(source, bytes)) LoadedImage.Vector(source, SVGDOM(Data.makeFromBytes(bytes)))
        else LoadedImage.Raster(rasters.getOrDecode(source) { Image.makeFromEncoded(bytes) })
}
