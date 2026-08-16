package cn.luorenmu.nutdraw.css

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
data class Edges(
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
    val left: Float = 0f,
) {
    constructor(all: Float) : this(all, all, all, all)
    constructor(vertical: Float, horizontal: Float) : this(vertical, horizontal, vertical, horizontal)
    val horizontal get() = left + right
    val vertical get() = top + bottom
}
