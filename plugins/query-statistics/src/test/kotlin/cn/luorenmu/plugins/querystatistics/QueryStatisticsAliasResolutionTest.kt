package cn.luorenmu.plugins.querystatistics

import cn.luorenmu.common.util.DatabaseBackend
import cn.luorenmu.common.util.DatabaseManager
import cn.luorenmu.repository.PlayerAliasRepository
import cn.luorenmu.repository.StatisticsRepository
import cn.luorenmu.repository.entity.AliasScope
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *
 * @author LoMu
 * Date 2026/8/16
 */
class QueryStatisticsAliasResolutionTest {
    @Test
    fun resolvesPersonalAliasBeforeLookingUpStatistics() {
        val directory = Files.createTempDirectory("query-statistics-alias-test")
        val manager = DatabaseManager(DatabaseBackend.SQLITE, directory.resolve("statistics.db"))
        try {
            manager.initialize()
            val aliasRepository = PlayerAliasRepository(manager)
            val statisticsRepository = StatisticsRepository(manager)
            aliasRepository.setAlias(
                alias = "队友",
                actualNickname = "RealPlayer",
                scope = AliasScope.PERSONAL,
                userId = "user-a",
                createdBy = "user-a",
            )
            statisticsRepository.recordPlayerQuery("RealPlayer", "user-a")
            statisticsRepository.recordPlayerQuery("RealPlayer", "user-a")

            val nickname = resolveStatisticsNickname(
                inputNickname = "队友",
                groupId = "group-a",
                senderId = "user-a",
                aliasRepository = aliasRepository,
            )

            assertEquals("RealPlayer", nickname)
            assertEquals(2, statisticsRepository.getNicknameQueryCount(nickname))
            assertEquals(2, statisticsRepository.getPlayerQueryCount("user-a", nickname))
        } finally {
            manager.close()
            directory.toFile().deleteRecursively()
        }
    }
}
