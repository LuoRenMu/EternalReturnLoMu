package cn.luorenmu.nutdraw.css

sealed interface CssSize {
    data object Auto : CssSize
    data class Px(val value: Float) : CssSize
    data class Percent(val value: Float) : CssSize
}

fun px(value: Number): CssSize = CssSize.Px(value.toFloat())
fun percent(value: Number): CssSize = CssSize.Percent(value.toFloat())
