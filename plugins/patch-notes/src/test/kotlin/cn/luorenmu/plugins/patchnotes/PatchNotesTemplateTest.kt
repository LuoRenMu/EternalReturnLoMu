package cn.luorenmu.plugins.patchnotes

import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.nutdraw.NutDraw
import cn.luorenmu.nutdraw.dom.NutElement
import cn.luorenmu.nutdraw.dom.NutNode
import cn.luorenmu.nutdraw.dom.NutText
import cn.luorenmu.nutdraw.layout.FlexLayoutEngine
import cn.luorenmu.nutdraw.layout.LayoutBox
import kotlinx.coroutines.runBlocking
import love.forte.simbot.common.id.ID
import love.forte.simbot.common.id.StringID
import love.forte.simbot.message.PlainText
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class PatchNotesTemplateTest {
    private val sample = PatchNoteRecord(
        PatchArticle(3867, "设计示例 · 最新更新摘要", "2026-10-01T02:00:00Z", "2026-10-01T02:00:00Z", "https://playeternalreturn.com/posts/news/3867"),
        PatchSummary("此图仅用于验证排版，不是实际 AI 分析结果。正式查询将返回定时扫描生成的官方更新重点摘要。", listOf(
            PatchSection("版本与系统", listOf("自动识别更新公告，中文整理新角色、系统与玩法变化。", "长公告分段分析后合并，尾部修复不会因正文截断而遗漏。")),
            PatchSection("角色调整 · 排版示例", listOf("妮琪：R 基础伤害 100/180/260 → 80/155/230。", "莉央：单弓 W 移动速度减少 30% → 25%；R 撞墙眩晕 1.2秒 → 1秒。", "卢克：R 标记伤害的攻击力系数 13% → 15%。")),
            PatchSection("修复与说明", listOf("同一文章再次查询直接读取缓存；官方修订公告后自动重新生成。", "图片校验失败时复用已持久化摘要重新出图，不重复请求 AI。")),
        )),
    )

    @Test fun `long summary grows without cropping sections or source`() {
        val template = PatchNotesTemplate()
        val long = sample.copy(summary = PatchSummary("中".repeat(180), listOf(PatchSection("角色", List(8) { "说明".repeat(180) }))))
        val doc = template.build(long)
        val layout = FlexLayoutEngine().layout(doc.root, doc.width.toFloat(), doc.height.toFloat())
        val footer = assertNotNull(layout.find("patch-footer"))
        val section = assertNotNull(layout.find("patch-section-0"))
        assertTrue(section.bounds.bottom < footer.bounds.top)
        assertTrue(footer.bounds.bottom <= layout.bounds.bottom)
        assertTrue(assertNotNull(layout.find("patch-point-0-7")).bounds.bottom <= section.bounds.bottom)
        val text = "中文한글🙂".repeat(40)
        assertEquals(text, template.wrap(text, 938f, 20f).joinToString(""))
    }

    @Test fun `command has latest update alias and no historical article parameter`() = runBlocking {
        val id = StringID::class.java.getMethod("valueOf", String::class.java).invoke(null, "test") as ID
        val sender = MessageSender(id, "test", id, "", "")
        val service = PatchNotesService(Files.createTempDirectory("latest-command"), { emptyList() },
            { error("unused") }, { _, _ -> error("unused") })
        val command = PatchNotesCommand { service }
        for (raw in listOf("../config", "-1", "0", "3867")) {
            assertTrue(assertIs<PlainText>(command.listen(sender, mapOf("articleId" to raw))).text.contains("最新更新正在分析"))
        }
        val annotation = PatchNotesCommand::class.java.getAnnotation(BotCommand::class.java)
        assertEquals("最新更新", annotation.alias)
        assertEquals("", annotation.value)
        assertTrue(command.optionals.isEmpty())
        assertEquals(setOf("更新公告", "更新日志"), annotation.aliases.toSet())
    }

    @Test fun `render patch design preview`() = runBlocking {
        val path = Path.of(System.getProperty("patch.design.root"), "build/previews/patch-notes-design.png")
        NutDraw.render(PatchNotesTemplate(), sample, path)
        assertTrue(Files.size(path) > 10_000)
    }

    @Test fun `official URL is retained in metadata but never drawn in image`() {
        fun texts(node: NutNode): List<String> = when (node) {
            is NutText -> listOf(node.value)
            is NutElement -> node.children.flatMap { texts(it) }
            else -> emptyList()
        }
        val content = texts(PatchNotesTemplate().build(sample).root)
        assertFalse(content.any { it.contains("https://") || it.contains(sample.article.url) })
        assertTrue(content.any { it.contains("官方原文") })
    }

    private fun LayoutBox.find(id: String): LayoutBox? = takeIf { node.id == id } ?: children.firstNotNullOfOrNull { it.find(id) }
}
