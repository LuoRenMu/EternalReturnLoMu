package cn.luorenmu.nutdraw.render

class ImageFormatDetector {
    fun isSvg(source: String, bytes: ByteArray): Boolean =
        source.substringBefore('?').endsWith(".svg", ignoreCase = true) ||
            bytes.decodeToString(0, minOf(bytes.size, 256)).contains("<svg", ignoreCase = true)
}
