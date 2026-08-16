package cn.luorenmu.nutdraw.css

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
sealed interface CssSize {
    data object Auto : CssSize
    data class Px(val value: Float) : CssSize
    data class Percent(val value: Float) : CssSize
}

fun px(value: Number): CssSize = CssSize.Px(value.toFloat())
fun percent(value: Number): CssSize = CssSize.Percent(value.toFloat())
