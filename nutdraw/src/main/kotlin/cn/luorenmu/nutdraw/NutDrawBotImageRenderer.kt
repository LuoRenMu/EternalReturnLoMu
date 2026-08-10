package cn.luorenmu.nutdraw

import cn.luorenmu.command.entity.CommandHelp
import cn.luorenmu.command.entity.RedemptionCodeActivityPage
import cn.luorenmu.nutdraw.template.TemplateManager
import cn.luorenmu.nutdraw.templates.*
import cn.luorenmu.render.BotImageRenderer
import cn.luorenmu.service.entity.CharacterDetail
import cn.luorenmu.service.entity.CharacterStats
import cn.luorenmu.service.entity.EternalReturnPlayRender
import cn.luorenmu.service.entity.TierStatistics
import java.nio.file.Path

/** Maps core render contracts to independent HTML/CSS-like NutDraw template classes. */
class NutDrawBotImageRenderer(
    private val templates: TemplateManager = TemplateManager(),
    private val help: HelpTemplate = HelpTemplate(),
    private val tiers: TierStatisticsTemplate = TierStatisticsTemplate(),
    private val activities: ActivityTemplate = ActivityTemplate(),
    private val stats: CharacterStatsTemplate = CharacterStatsTemplate(),
    private val detail: CharacterDetailTemplate = CharacterDetailTemplate(),
    private val player: SearchPlayerTemplate = SearchPlayerTemplate(),
) : BotImageRenderer {
    override suspend fun renderHelp(data: CommandHelp, output: Path) = templates.render(help, data, output)
    override suspend fun renderTierStatistics(data: TierStatistics, output: Path) = templates.render(tiers, data, output)
    override suspend fun renderRedemptionCodeActivities(data: RedemptionCodeActivityPage, output: Path) = templates.render(activities, data, output)
    override suspend fun renderCharacterStats(data: CharacterStats, output: Path) = templates.render(stats, data, output)
    override suspend fun renderCharacterDetail(data: CharacterDetail, output: Path) = templates.render(detail, data, output)
    override suspend fun renderSearchPlayer(data: EternalReturnPlayRender, output: Path) = templates.render(player, data, output)
}
