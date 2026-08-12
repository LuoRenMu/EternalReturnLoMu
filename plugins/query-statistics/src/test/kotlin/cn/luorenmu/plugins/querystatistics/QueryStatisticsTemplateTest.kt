package cn.luorenmu.plugins.querystatistics

import cn.luorenmu.nutdraw.NutDraw
import cn.luorenmu.repository.entity.PlayerQueryHistoryRecord
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertTrue

class QueryStatisticsTemplateTest {
    @Test
    fun rendersPlayerHistoryImage() = runBlocking {
        val output = Path.of(System.getProperty("user.dir"), "build", "previews", "query-statistics.png")
        Files.createDirectories(output.parent)
        NutDraw.render(
            QueryStatisticsTemplate(),
            QueryStatisticsData(
                senderName = "洛木",
                history = listOf(
                    PlayerQueryHistoryRecord("神圣审判", 18, LocalDateTime.now(), LocalDateTime.of(2026, 8, 12, 1, 8)),
                    PlayerQueryHistoryRecord("RIOORI", 7, LocalDateTime.now(), LocalDateTime.of(2026, 8, 11, 22, 36)),
                    PlayerQueryHistoryRecord("한동그라미", 3, LocalDateTime.now(), LocalDateTime.of(2026, 8, 10, 19, 20)),
                    PlayerQueryHistoryRecord("LuoMu", 1, LocalDateTime.now(), LocalDateTime.of(2026, 8, 9, 12, 5)),
                ),
            ),
            output,
        )
        assertTrue(Files.size(output) > 100)
    }
}
