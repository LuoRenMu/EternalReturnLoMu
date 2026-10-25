package cn.luorenmu.plugins.match

import cn.luorenmu.Adapter
import cn.luorenmu.command.CommandEvent
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.util.NickNameUtil
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.nutdraw.NutDraw
import love.forte.simbot.message.Message
import love.forte.simbot.message.OfflineImage
import love.forte.simbot.message.toText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path

@BotCommand("查询对局", "查询对局", "<gameId> <nickname> <seasonId>",
    aliases = ["对局详情"], adapter = [Adapter.QG_BOT, Adapter.ONE_BOT])
class MatchDetailCommand(
    private val query: suspend (Long, String?, Long?) -> MatchDetailData = { id, name, season -> MatchDetailSource.query(id, name, season) },
    private val render: suspend (MatchDetailData, Path) -> Unit = { data, target -> NutDraw.render(MatchDetailTemplate(), data, target) },
) : CommandEvent {
    override val description = "查询真实对局结算详情，可点击玩家查询中的最近三局按钮"
    override val example = "/查询对局 65932204 백수 41"
    override val optionals = listOf(
        CommandOptional("gameId", "对局 ID", true),
        CommandOptional("nickname", "对局中的玩家昵称；按钮会自动携带", false),
        CommandOptional("seasonId", "赛季 ID（钴协议为 0）；按钮会自动携带", false),
    )
    private val locks = Array(32) { Mutex() }

    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message {
        val id = command["gameId"]?.toLongOrNull()?.takeIf { it > 0 }
            ?: return "请输入有效对局 ID，或点击玩家查询中的对局按钮".toText()
        val name = command["nickname"]?.takeIf { it.isNotBlank() }
        val rawSeason = command["seasonId"]?.takeIf { it.isNotBlank() }
        val season = rawSeason?.toLongOrNull()?.takeIf { it >= 0 }
        if (name != null && !NickNameUtil.isValidNickname(name)) return "玩家昵称不合法".toText()
        if ((name == null) != (rawSeason == null) || rawSeason != null && season == null)
            return "请同时提供玩家昵称和非负赛季 ID，或直接点击最近对局按钮".toText()
        return locks[Math.floorMod(id.hashCode(), locks.size)].withLock {
            val data = query(id, name, season)
            check(!data.designSample && data.gameId == id.toString()) { "对局数据与请求不匹配" }
            val target = PathUtils.resourcesPathResolve("render", "match", "$id-${data.focusedPlayerId ?: "all"}.png")
            render(data, target)
            OfflineImage.fileOfflineImage(target.toString())
        }
    }
}
