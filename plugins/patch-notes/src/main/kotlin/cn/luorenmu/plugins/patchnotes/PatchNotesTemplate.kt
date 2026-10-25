package cn.luorenmu.plugins.patchnotes

import cn.luorenmu.nutdraw.css.*
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.nutdraw.render.CjkFontResolver
import cn.luorenmu.nutdraw.template.ImageTemplate
import cn.luorenmu.nutdraw.template.TemplateDocument
import org.jetbrains.skia.Color
import java.nio.file.Path

/** Fixed readable typography; long summaries grow vertically instead of truncating or shrinking. */
class PatchNotesTemplate(private val articleDirectory: Path? = null) : ImageTemplate<PatchNoteRecord> {
    private val fonts = CjkFontResolver()
    private val ink = Color.makeRGB(32, 38, 49)
    private val muted = Color.makeRGB(99, 107, 119)
    private val accent = Color.makeRGB(48, 104, 174)

    override fun build(data: PatchNoteRecord): TemplateDocument {
        val title = wrap(data.article.title, 1000f, 32f)
        val headline = wrap(data.summary.headline, 1000f, 22f)
        val sections = data.summary.sections.map { section ->
            wrap(section.title, 970f, 24f) to section.points.map { wrap(it, 938f, 20f) }
        }
        val headerHeight = 24 + title.size * 44 + 26 + (title.size + 1) * 6
        val overviewHeight = 36 + headline.size * 32
        val sectionHeights = sections.map { (label, points) -> 40 + label.size * 34 + 12 + points.sumOf { it.size * 30 } + (points.size - 1) * 12 }
        val images = data.images.take(3)
        val heroHeight = images.firstOrNull()?.let { (1000f * it.height / it.width).toInt().coerceIn(160, 440) } ?: 0
        val galleryHeight = if (images.isEmpty()) 0 else 32 + 40 + heroHeight + 24 +
            if (images.size > 1) 16 + 310 else 0
        val height = 64 + headerHeight + overviewHeight + galleryHeight + sectionHeights.sum() + 34 +
            (sections.size + 2 + if (images.isEmpty()) 0 else 1) * 18
        return TemplateDocument(1120, height, document(CssStyle(width = px(1120), height = px(height),
            padding = Edges(32f), gap = 18f, background = Color.makeRGB(245, 246, 248), color = ink), "patch-page") {
            Column(CssStyle(width = px(1056), height = px(headerHeight), gap = 6f)) {
                Text("永恒轮回 / 最新更新", style(16f, 24f, accent), "patch-label")
                title.forEachIndexed { i, line -> Text(line, style(32f, 44f, ink, 700), "patch-title-$i") }
                Text("文章 ${data.article.id} · 发布 ${data.article.createdAt.take(10)} · AI 中文重点摘要",
                    style(16f, 26f, muted), "patch-meta")
            }
            Column(CssStyle(width = px(1056), height = px(overviewHeight), padding = Edges(18f, 28f),
                background = Color.makeRGB(233, 241, 251), borderRadius = 8f), "patch-overview") {
                headline.forEach { Text(it, style(22f, 32f)) }
            }
            if (images.isNotEmpty()) Column(CssStyle(width = px(1056), height = px(galleryHeight),
                padding = Edges(16f, 28f), background = Color.WHITE, borderRadius = 8f), "patch-body-images") {
                Text("官方正文配图", style(20f, 32f, accent, 700), "patch-images-label")
                Column(CssStyle(width = px(1000), height = px(8))) {}
                Image(imageSource(images.first()), CssStyle(width = px(1000), height = px(heroHeight),
                    objectFit = ObjectFit.CONTAIN, borderRadius = 6f), "patch-body-image-0")
                Text(images.first().caption.take(50), style(16f, 24f, muted), "patch-body-caption-0")
                if (images.size > 1) {
                    Column(CssStyle(width = px(1000), height = px(16))) {}
                    Row(CssStyle(width = px(1000), height = px(310), gap = 20f)) {
                        images.drop(1).forEachIndexed { index, image ->
                            Column(CssStyle(width = px(490), height = px(310))) {
                                Image(imageSource(image), CssStyle(width = px(490), height = px(262), objectFit = ObjectFit.CONTAIN,
                                    borderRadius = 6f), "patch-body-image-${index + 1}")
                                wrap(image.caption.take(50), 490f, 16f).take(2).forEach {
                                    Text(it, style(16f, 24f, muted).copy(width = px(490)))
                                }
                            }
                        }
                    }
                }
            }
            sections.forEachIndexed { index, (label, points) ->
                Column(CssStyle(width = px(1056), height = px(sectionHeights[index]), padding = Edges(20f, 28f),
                    background = Color.WHITE, borderRadius = 8f), "patch-section-$index") {
                    label.forEach { Text(it, style(24f, 34f, accent, 700)) }
                    Column(CssStyle(width = px(1000), height = px(12))) {}
                    points.forEachIndexed { pointIndex, lines ->
                        Row(CssStyle(width = px(1000), height = px(lines.size * 30), gap = 14f), "patch-point-$index-$pointIndex") {
                            Text("•", style(20f, 30f, accent).copy(width = px(18)))
                            Column(CssStyle(width = px(968), height = px(lines.size * 30))) {
                                lines.forEach { Text(it, style(20f, 30f).copy(width = px(968))) }
                            }
                        }
                        if (pointIndex != points.lastIndex) Column(CssStyle(width = px(1000), height = px(12))) {}
                    }
                }
            }
            Column(CssStyle(width = px(1056), height = px(34)), "patch-footer") {
                Text("AI 摘要不覆盖全部改动，具体数值与最终生效内容请以官方原文为准。", style(17f, 26f, muted))
            }
        })
    }

    private fun imageSource(image: PatchBodyImage): String = articleDirectory?.let {
        require(image.fileName.matches(Regex("media/[0-9a-f]{20}\\.png")))
        it.resolve(image.fileName).toUri().toString()
    } ?: image.sourceUrl

    private fun style(size: Float, height: Float, color: Int = ink, weight: Int = 400) = CssStyle(
        width = px(1000), height = px(height), fontSize = size, lineHeight = height / size,
        color = color, fontWeight = weight, verticalAlign = VerticalAlign.CENTER)

    internal fun wrap(text: String, width: Float, size: Float): List<String> {
        val result = mutableListOf<String>()
        text.lineSequence().forEach { paragraph ->
            var line = ""
            paragraph.codePoints().forEach { code ->
                val char = String(Character.toChars(code))
                val candidate = line + char
                val measured = fonts.runs(candidate, size, 700).sumOf { it.width.toDouble() }
                if (line.isNotEmpty() && measured > width) {
                    result += line
                    line = char
                } else line = candidate
            }
            result += line
        }
        return result.ifEmpty { listOf("") }
    }
}
