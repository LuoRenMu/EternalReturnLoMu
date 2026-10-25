package cn.luorenmu.plugins.patchnotes

import cn.luorenmu.request.api.entity.response.official.EternalReturnNews
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/** Optional live-fetched fixtures are in ignored build/, never include private configuration or tokens. */
class PatchNotesOfficialFixtureTest {
    @Test fun `current official category response and HTML work through DTO and AI body extraction`() = runBlocking {
        val root = Path.of(System.getProperty("patch.design.root"), "build/patch-fixtures")
        if (!Files.exists(root.resolve("list.json")) || !Files.exists(root.resolve("3867.html"))) return@runBlocking
        val response = Json { ignoreUnknownKeys = true }.decodeFromString<EternalReturnNews>(Files.readString(root.resolve("list.json")))
        assertTrue(response.articles.all { it.categoryId == 28 })
        assertTrue(response.articles.any { it.id == 3867 && it.i18ns.zhCN?.title?.contains("12.5") == true })
        val chunks = mutableListOf<String>()
        val summarizer = PatchNotesSummarizer { _, user ->
            chunks += user
            """{"headline":"这是测试桩，不是实际AI输出","sections":[{"title":"验证","points":["正文已提取"]}]}"""
        }
        assertNotNull(summarizer.summarize("12.5", Files.readString(root.resolve("3867.html"))))
        assertTrue(chunks.first().contains("标题：12.5"))
        assertTrue(chunks.size >= 2)
        println("Official fixture validated: articleId=3867; AI-body chunks/merge calls=${chunks.size}; LLM stub only")
    }
}
