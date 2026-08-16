package cn.luorenmu.nutdraw.render

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class ImageFormatDetector {
    fun isSvg(source: String, bytes: ByteArray): Boolean =
        source.substringBefore('?').endsWith(".svg", ignoreCase = true) ||
            bytes.decodeToString(0, minOf(bytes.size, 256)).contains("<svg", ignoreCase = true)
}
