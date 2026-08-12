package cn.luorenmu.repository

import cn.luorenmu.common.util.DatabaseBackend
import cn.luorenmu.common.util.DatabaseManager
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class StatisticsRepositoryPlayerQueryTest {
    @Test
    fun recordsGlobalAndPerUserPlayerQueryCounts() {
        val directory = Files.createTempDirectory("player-query-statistics-test")
        val manager = DatabaseManager(DatabaseBackend.SQLITE, directory.resolve("statistics.db"))
        try {
            manager.initialize()
            val repository = StatisticsRepository(manager)

            repository.recordPlayerQuery("Alpha", "user-a")
            repository.recordPlayerQuery("Alpha", "user-a")
            repository.recordPlayerQuery("Alpha", "user-b")
            repository.recordPlayerQuery("Beta", "user-a")

            assertEquals(3, repository.getNicknameQueryCount("Alpha"))
            assertEquals(2, repository.getPlayerQueryCount("user-a", "Alpha"))
            assertEquals(listOf("Beta", "Alpha"), repository.getPlayerQueryHistory("user-a").map { it.nickname })
        } finally {
            manager.close()
            directory.toFile().deleteRecursively()
        }
    }
}
