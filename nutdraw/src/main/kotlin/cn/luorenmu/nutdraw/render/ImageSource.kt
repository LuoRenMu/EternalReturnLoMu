package cn.luorenmu.nutdraw.render

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
fun interface ImageSource {
    fun load(source: String): ByteArray?
}
