package cn.luorenmu.nutdraw

import cn.luorenmu.nutdraw.css.*
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.nutdraw.render.SkiaDocumentRenderer
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Color
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class CjkFontRenderingTest {
    @Test
    fun rendersChineseJapaneseAndKoreanInOneDocument() = runBlocking {
        val output = Path.of(System.getProperty("user.dir"), "build", "previews", "cjk-font-skia.png")
        val root = document(CssStyle(width = px(920), height = px(360), padding = Edges(36f), gap = 16f, background = Color.makeRGB(15,17,23))) {
            text("中日韩字体回退测试", CssStyle(width = percent(100), height = px(52), fontSize = 34f, fontWeight = 700, color = Color.WHITE))
            text("中文：永恒轮回 · 玩家战绩 · 武器流派", lineStyle())
            text("日本語：エターナルリターン・戦績・武器ビルド", lineStyle())
            text("한국어：이터널 리턴 · 전적 · 무기 빌드", lineStyle())
            text("混排：神聖審判 / 神圣审判 / 聖なる審判 / 신성한 심판", lineStyle())
        }
        Files.createDirectories(output.parent)
        SkiaDocumentRenderer().render(root, output, 920, 360)
        assertTrue(Files.size(output) > 1_000)
        assertContentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), Files.readAllBytes(output).take(4).toByteArray())
    }

    private fun lineStyle() = CssStyle(width = percent(100), height = px(42), fontSize = 23f, color = Color.makeRGB(225,228,237))
}
