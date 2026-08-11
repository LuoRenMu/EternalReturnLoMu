package cn.luorenmu.nutdraw.render

import cn.luorenmu.nutdraw.css.ObjectFit
import cn.luorenmu.nutdraw.css.TextAlign
import cn.luorenmu.nutdraw.css.VerticalAlign
import cn.luorenmu.nutdraw.dom.NutNode
import cn.luorenmu.nutdraw.dom.*
import cn.luorenmu.nutdraw.layout.FlexLayoutEngine
import cn.luorenmu.nutdraw.layout.LayoutBox
import cn.luorenmu.nutdraw.resource.ResourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max
import kotlin.math.min

class SkiaDocumentRenderer(
    private val layoutEngine: FlexLayoutEngine = FlexLayoutEngine(),
    resources: ResourceManager = ResourceManager(),
) {
    private val imageLoader = resources.images
    private val fontResolver = resources.fonts
    private val vectors = VectorImageCache()
    private val lineChartRenderer = LineChartRenderer(fontResolver)
    suspend fun render(root: NutNode, output: Path, width: Int, height: Int): Path = withContext(Dispatchers.IO) {
        output.parent?.let(Files::createDirectories)
        Surface.makeRasterN32Premul(width, height).use { surface ->
            surface.canvas.clear(root.style.background)
            draw(surface.canvas, layoutEngine.layout(root, width.toFloat(), height.toFloat()))
            Files.write(output, checkNotNull(surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)).bytes)
        }
        output
    }

    private fun draw(canvas: Canvas, box: LayoutBox) {
        val style = box.node.style
        val shape = style.cornerRadii?.let { radii ->
            RRect.makeComplexLTRB(box.bounds.left, box.bounds.top, box.bounds.right, box.bounds.bottom, floatArrayOf(
                radii.topLeft, radii.topLeft, radii.topRight, radii.topRight,
                radii.bottomRight, radii.bottomRight, radii.bottomLeft, radii.bottomLeft,
            ))
        } ?: RRect.makeXYWH(box.bounds.left, box.bounds.top, box.bounds.width, box.bounds.height, style.borderRadius)
        if (Color.getA(style.background) > 0) canvas.drawRRect(shape, Paint().apply { color = style.background; isAntiAlias = true })
        style.backgroundImage?.let { source ->
            imageLoader.load(source)?.use { image ->
                canvas.save()
                canvas.clipRRect(shape, true)
                drawLoadedImage(canvas, image, box.bounds, ObjectFit.COVER)
                canvas.restore()
            }
        }
        if (style.border.width > 0f) canvas.drawRRect(shape, Paint().apply { color = style.border.color; mode = PaintMode.STROKE; strokeWidth = style.border.width; isAntiAlias = true })
        when (val node = box.node) {
            is NutText -> drawText(canvas, node, box)
            is NutImage -> drawImage(canvas, node, box)
            is NutLineChart -> lineChartRenderer.draw(canvas, node, box.bounds)
            is NutElement -> box.children.forEach { draw(canvas, it) }
        }
    }

    private fun drawText(canvas: Canvas, node: NutText, box: LayoutBox) {
        val s = node.style
        val runs = fontResolver.runs(node.value, s.fontSize, s.fontWeight)
        val width = runs.sumOf { it.width.toDouble() }.toFloat()
        val x = when (s.textAlign) { TextAlign.CENTER -> box.bounds.left + (box.bounds.width - width) / 2; TextAlign.END -> box.bounds.right - width - s.padding.right; else -> box.bounds.left + s.padding.left }
        val metrics = runs.firstOrNull()?.font?.metrics
        val baselineOffset = if (metrics == null) s.fontSize else -(metrics.ascent + metrics.descent) / 2f
        val y = when (s.verticalAlign) {
            VerticalAlign.CENTER -> box.bounds.top + box.bounds.height / 2f + baselineOffset
            VerticalAlign.END -> box.bounds.bottom - s.padding.bottom - (metrics?.descent ?: 0f)
            VerticalAlign.START -> box.bounds.top + s.padding.top + s.fontSize
        }
        canvas.save()
        canvas.clipRect(box.bounds)
        val paint = Paint().apply { color = s.color; isAntiAlias = true }
        var cursor = x
        runs.forEach { run ->
            canvas.drawString(run.text, cursor, y, run.font, paint)
            cursor += run.width
        }
        canvas.restore()
    }

    private fun drawImage(canvas: Canvas, node: NutImage, box: LayoutBox) {
        imageLoader.load(node.source)?.use { image ->
            canvas.save()
            val shape = node.style.cornerRadii?.let { radii ->
                RRect.makeComplexLTRB(box.bounds.left, box.bounds.top, box.bounds.right, box.bounds.bottom, floatArrayOf(
                    radii.topLeft, radii.topLeft, radii.topRight, radii.topRight,
                    radii.bottomRight, radii.bottomRight, radii.bottomLeft, radii.bottomLeft,
                ))
            } ?: RRect.makeXYWH(box.bounds.left, box.bounds.top, box.bounds.width, box.bounds.height, node.style.borderRadius)
            canvas.clipRRect(shape, true)
            drawLoadedImage(canvas, image, box.bounds, node.style.objectFit)
            canvas.restore()
        }
    }

    private fun drawLoadedImage(canvas: Canvas, loaded: LoadedImage, target: Rect, fit: ObjectFit) = when (loaded) {
        is LoadedImage.Raster -> {
            val image = loaded.image
            val sourceRatio = image.width.toFloat() / image.height
            val targetRatio = target.width / max(1f, target.height)
            val fitted = when (fit) {
                ObjectFit.FILL, ObjectFit.COVER -> target
                ObjectFit.CONTAIN -> if (sourceRatio > targetRatio) {
                    val h = target.width / sourceRatio; Rect.makeXYWH(target.left, target.top + (target.height - h) / 2, target.width, h)
                } else {
                    val w = target.height * sourceRatio; Rect.makeXYWH(target.left + (target.width - w) / 2, target.top, w, target.height)
                }
            }
            if (fit == ObjectFit.COVER) drawCover(canvas, image, target) else canvas.drawImageRect(image, fitted)
        }
        is LoadedImage.Vector -> {
            val width = target.width.toInt().coerceAtLeast(1)
            val height = target.height.toInt().coerceAtLeast(1)
            val image = vectors.getOrRender(VectorImageKey(loaded.source, width, height)) {
                loaded.dom.setContainerSize(width.toFloat(), height.toFloat())
                Surface.makeRasterN32Premul(width, height).use { surface ->
                    surface.canvas.clear(Color.TRANSPARENT)
                    loaded.dom.render(surface.canvas)
                    surface.makeImageSnapshot()
                }
            }
            canvas.drawImageRect(image, target)
        }
    }

    private fun drawCover(canvas: Canvas, image: Image, target: Rect) {
        val sourceRatio = image.width.toFloat() / image.height
        val targetRatio = target.width / max(1f, target.height)
        val source = if (sourceRatio > targetRatio) {
            val sourceWidth = image.height * targetRatio
            Rect.makeXYWH((image.width - sourceWidth) / 2f, 0f, sourceWidth, image.height.toFloat())
        } else {
            val sourceHeight = image.width / targetRatio
            Rect.makeXYWH(0f, (image.height - sourceHeight) / 2f, image.width.toFloat(), sourceHeight)
        }
        canvas.drawImageRect(image, source, target, SamplingMode.LINEAR, Paint(), false)
    }
}
