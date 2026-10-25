package cn.luorenmu.plugins.patchnotes

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PatchNotesSummarizerTest {
    private val answer = """{"headline":"更新重点","sections":[{"title":"系统","points":["新增功能"]}]}"""

    @Test fun `only article body is extracted and script instructions are excluded`() {
        val text = PatchNotesSummarizer.articleText("<nav>菜单</nav><div class='er-article-detail__content er-article-content fr-view'><p>角色调整</p><script>恶意指令</script><p>尾部修复</p></div>")
        assertTrue(text.contains("角色调整"))
        assertTrue(text.contains("尾部修复"))
        assertFalse(text.contains("菜单"))
        assertFalse(text.contains("恶意指令"))
        assertFailsWith<IllegalArgumentException> { PatchNotesSummarizer.articleText("<html>Access denied</html>") }
    }

    @Test fun `long articles include final chunk and then merge every partial`() = runBlocking {
        val inputs = mutableListOf<String>()
        val summarizer = PatchNotesSummarizer { _, user -> inputs += user; answer }
        assertNotNull(summarizer.summarize("12.5", "中".repeat(20_010) + "尾部重要修复"))
        assertEquals(4, inputs.size)
        assertTrue(inputs[2].contains("尾部重要修复"))
        assertTrue(inputs.last().contains("合并"))
    }

    @Test fun `invalid or disabled AI result is not treated as a completed summary`() = runBlocking {
        assertNull(PatchNotesSummarizer { _, _ -> null }.summarize("test", "正文"))
        assertNull(PatchNotesSummarizer { _, _ -> "{\"headline\":\"\",\"sections\":[]}" }.summarize("test", "正文"))
        assertNotNull(PatchNotesSummarizer { _, _ -> "```json\n$answer\n```" }.summarize("test", "正文"))
        Unit
    }

    @Test fun `cancellation is propagated`() {
        assertFailsWith<CancellationException> {
            runBlocking { PatchNotesSummarizer { _, _ -> throw CancellationException() }.summarize("test", "正文") }
        }
    }

    @Test fun `character adjustments identify each affected character and retain their own changes`() = runBlocking {
        val result = """{"headline":"角色平衡","sections":[{"title":"角色调整","points":["妮琪：R 基础伤害 100/180/260→80/155/230。","卢克：R 攻击力系数 13%→15%。"]}]}"""
        val systems = mutableListOf<String>()
        val summary = assertNotNull(PatchNotesSummarizer { system, _ -> systems += system; result }.summarize("12.5",
            "<article><h5>实验体</h5><p><strong>妮琪</strong></p><p>R基础伤害100/180/260→80/155/230。</p>" +
                "<p><strong>卢克</strong></p><p>R攻击力系数13%→15%。</p></article>"))
        assertEquals(listOf("妮琪：R 基础伤害 100/180/260→80/155/230。", "卢克：R 攻击力系数 13%→15%。"), summary.sections.single().points)
        assertTrue(systems.single().contains("每条必须以“角色名：”开头"))
        assertTrue(PatchNotesSummarizer.articleText("<article><p>妮琪</p><p>R改动</p></article>").contains("妮琪\nR改动"))
    }

    @Test fun `unnamed generic or invented characters are not accepted as a successful summary`() = runBlocking {
        for (point in listOf("R 基础伤害降低。", "多个角色：伤害降低。", "不存在的角色：R伤害降低。", "妮琪：")) {
            val result = """{"headline":"角色平衡","sections":[{"title":"角色调整","points":["$point"]}]}"""
            assertNull(PatchNotesSummarizer { _, _ -> result }.summarize("12.5", "妮琪：R伤害降低。多个角色调整。"), point)
        }
    }

    @Test fun `English source names remain attributable when Chinese article is unavailable`() = runBlocking {
        val result = """{"headline":"角色平衡","sections":[{"title":"角色调整","points":["Luke: R 攻击力系数 13%→15%。"]}]}"""
        assertNotNull(PatchNotesSummarizer { _, _ -> result }.summarize("12.5", "Luke\nR attack power ratio 13%→15%."))
        Unit
    }

    @Test fun `skill words in official body cannot replace the character heading`() = runBlocking {
        val html = "<article><h5>实验体</h5><p><strong>妮琪</strong></p><p>修复<strong>格挡</strong>的减速效果。</p></article>"
        val result = """{"headline":"角色平衡","sections":[{"title":"角色调整","points":["格挡：修复减速效果。"]}]}"""
        assertNull(PatchNotesSummarizer { _, _ -> result }.summarize("12.5", html))
    }

    @Test fun `new character header supplies a source name even without a standalone bold label`() = runBlocking {
        val result = """{"headline":"新角色","sections":[{"title":"新角色","points":["塞莉丝：新增近战角色。"]}]}"""
        assertNotNull(PatchNotesSummarizer { _, _ -> result }.summarize("12.5",
            "<article><h3>新实验体 - 塞莉丝</h3><p>新增近战角色。</p><p><strong>其他内容</strong></p></article>"))
        Unit
    }

    @Test fun `heading context survives a chunk boundary and merge preserves character labels`() = runBlocking {
        val inputs = mutableListOf<String>()
        val result = """{"headline":"角色平衡","sections":[{"title":"角色调整","points":["妮琪：R伤害降低。"]}]}"""
        var calls = 0
        val summary = assertNotNull(PatchNotesSummarizer { _, user ->
            inputs += user
            calls++
            if (calls == 1) answer else result
        }.summarize("12.5", "中".repeat(9_980) + "\n妮琪\n" + "R技能说明".repeat(10)))
        assertEquals(3, calls)
        assertTrue(inputs[1].substringBefore("正文分段").contains("妮琪"))
        assertTrue(inputs.last().contains("妮琪：R伤害降低。"))
        assertEquals(listOf("妮琪：R伤害降低。"), summary.sections.single().points)
    }

    @Test fun `merge cannot remove all character labels or introduce another character`() = runBlocking {
        val partial = """{"headline":"角色平衡","sections":[{"title":"角色调整","points":["妮琪：R伤害降低。"]}]}"""
        val wrongCharacter = """{"headline":"角色平衡","sections":[{"title":"角色调整","points":["卢克：R伤害降低。"]}]}"""
        val unnamed = """{"headline":"角色平衡","sections":[{"title":"角色调整","points":["多个角色的技能调整。"]}]}"""
        for (merged in listOf(answer, wrongCharacter, unnamed)) {
            var calls = 0
            assertNull(PatchNotesSummarizer { _, _ -> if (++calls == 3) merged else partial }
                .summarize("12.5", "妮琪" + "中".repeat(9_998) + "妮琪R伤害降低。"))
            assertEquals(3, calls)
        }
    }
}
