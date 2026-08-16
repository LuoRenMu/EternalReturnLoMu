package cn.luorenmu.plugins.news

import cn.luorenmu.command.entity.RedemptionCodeActivityPage
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class RedemptionCodeActivitySkiaTest {
    @Test
    fun rendersPngWithoutBrowser() = runBlocking {
        val output = Path.of(System.getProperty("user.dir"), "build", "previews", "activity-skia.png")
        Files.createDirectories(output.parent)
        cn.luorenmu.nutdraw.NutDraw.render(
            ActivityTemplate(),
                RedemptionCodeActivityPage(
                    generatedDate = "2026-08-09",
                    items = listOf(
                        RedemptionCodeActivityPage.Item(
                            title = "Test activity",
                            code = "ABC123",
                            reward = "Test reward",
                            note = "Test note",
                            period = "2026-08-01 - 2026-08-10",
                            status = "Active",
                            thumbnailUrl = null,
                        ),
                    ),
                ),
                output,
        )
        assertTrue(Files.size(output) > 100)
        assertContentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
            Files.readAllBytes(output).take(4).toByteArray(),
        )
        println("Skia preview retained at: $output")
    }
}
