package cn.luorenmu.nutdraw.css

import org.jetbrains.skia.Color

data class Border(val width: Float = 0f, val color: Int = Color.TRANSPARENT)
data class CornerRadii(val topLeft: Float, val topRight: Float, val bottomRight: Float, val bottomLeft: Float)
