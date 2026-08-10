package cn.luorenmu.render

import cn.luorenmu.command.entity.CommandHelp
import cn.luorenmu.command.entity.RedemptionCodeActivityPage
import cn.luorenmu.service.entity.CharacterDetail
import cn.luorenmu.service.entity.CharacterStats
import cn.luorenmu.service.entity.EternalReturnPlayRender
import cn.luorenmu.service.entity.TierStatistics
import java.nio.file.Path

/**
 * Typed boundary for bot image rendering.
 *
 * Core owns the render models and contract. Runtime adapters provide the implementation,
 * keeping the Skia implementation out of core and avoiding a core/nutdraw dependency cycle.
 */
interface BotImageRenderer {
    suspend fun renderHelp(data: CommandHelp, output: Path): Path

    suspend fun renderTierStatistics(data: TierStatistics, output: Path): Path

    suspend fun renderRedemptionCodeActivities(data: RedemptionCodeActivityPage, output: Path): Path

    suspend fun renderCharacterStats(data: CharacterStats, output: Path): Path

    suspend fun renderCharacterDetail(data: CharacterDetail, output: Path): Path

    suspend fun renderSearchPlayer(data: EternalReturnPlayRender, output: Path): Path
}
