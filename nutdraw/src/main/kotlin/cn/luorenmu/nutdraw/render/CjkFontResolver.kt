package cn.luorenmu.nutdraw.render

/**
 * Stable text-layout interface; fallback selection and run construction remain internal modules.
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class CjkFontResolver(private val runBuilder: FontRunBuilder = FontRunBuilder()) {
    fun runs(text: String, size: Float, weight: Int): List<FontRun> = runBuilder.build(text, size, weight)
}
