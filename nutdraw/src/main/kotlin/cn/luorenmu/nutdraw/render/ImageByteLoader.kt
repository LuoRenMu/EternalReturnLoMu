package cn.luorenmu.nutdraw.render

class ImageByteLoader(
    private val sources: List<ImageSource> = listOf(HttpImageSource(), FileImageSource(), ResourceImageSource(), ClasspathImageSource()),
) {
    fun load(source: String): ByteArray = sources.firstNotNullOfOrNull { adapter ->
        runCatching { adapter.load(source) }.getOrNull()
    } ?: error("Image not found: $source")
}
