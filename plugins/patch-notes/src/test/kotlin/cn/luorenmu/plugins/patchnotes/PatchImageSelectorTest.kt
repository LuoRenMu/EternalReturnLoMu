package cn.luorenmu.plugins.patchnotes

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PatchImageSelectorTest {
    private val summary = PatchSummary("重点", listOf(PatchSection("新角色", listOf("新增角色"))))
    private val candidates = listOf(PatchImageCandidate(0, "新角色", "角色介绍"), PatchImageCandidate(8, "系统", "背包调整"))

    @Test fun `select known numeric IDs using summary and article context`() = runBlocking {
        val selector = PatchImageSelector { system, user ->
            assertTrue(system.contains("未提供像素"))
            assertTrue(user.contains("背包调整"))
            assertTrue(user.contains("新增角色"))
            "```json\n{\"imageIds\":[8,0]}\n```"
        }
        assertEquals(listOf(8, 0), selector.select(summary, candidates))
    }

    @Test fun `unknown duplicate too many or malformed IDs never become trusted selection`() = runBlocking {
        for (raw in listOf("{\"imageIds\":[99]}", "{\"imageIds\":[0,0]}", "{\"imageIds\":[0,8,0,8]}",
            "{\"imageIds\":[\"https://evil.example/a.png\"]}", "not json")) {
            assertNull(PatchImageSelector { _, _ -> raw }.select(summary, candidates))
        }
        assertNull(PatchImageSelector { _, _ -> null }.select(summary, candidates))
    }

    @Test fun `cancellation is propagated`() {
        assertFailsWith<CancellationException> {
            runBlocking { PatchImageSelector { _, _ -> throw CancellationException() }.select(summary, candidates) }
        }
    }
}
