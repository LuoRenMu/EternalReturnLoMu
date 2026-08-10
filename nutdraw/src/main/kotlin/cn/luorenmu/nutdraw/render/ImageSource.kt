package cn.luorenmu.nutdraw.render

fun interface ImageSource {
    fun load(source: String): ByteArray?
}
