package cn.luorenmu.nutdraw.render

class ImageLoader(
    private val bytes: ImageByteLoader = ImageByteLoader(),
    private val cache: ImageByteCache = ImageByteCache(),
    private val decoder: ImageDecoder = ImageDecoder(),
) {
    fun load(source: String?): LoadedImage? = source?.takeIf(String::isNotBlank)?.let { value ->
        runCatching { decoder.decode(value, cache.getOrLoad(value) { bytes.load(value) }) }.getOrNull()
    }
}
