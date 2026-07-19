package cn.luorenmu.command

import cn.luorenmu.Adapter
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.annotation.BotCommand
import cn.luorenmu.common.util.NickNameUtil
import cn.luorenmu.repository.PlayerAliasRepository
import cn.luorenmu.repository.entity.AliasScope
import io.github.oshai.kotlinlogging.KotlinLogging
import love.forte.simbot.component.qguild.message.QGMarkdown
import love.forte.simbot.message.Message
import love.forte.simbot.message.toText
import org.koin.java.KoinJavaComponent.inject

/**
 * 玩家别名管理命令。
 *
 *
 * @author LoMu
 * Date 2026/6/2
 */
@BotCommand(
    "玩家别名",
    "玩家别名",
    "<action> <alias> <nickname> <scope>",
    adapter = [Adapter.QG_BOT, Adapter.ONE_BOT],
)
class PlayerAliasCommand : CommandEvent {
    override val example: String = "/玩家别名 set 五字 한동그라미 群"
    override val optionals: List<CommandOptional> =
        listOf(
            CommandOptional(name = "action", description = "set / del / list", required = true),
            CommandOptional(name = "alias", description = "别名（set/del 时为别名，当为“我”时表示自己）", required = false),
            CommandOptional(name = "nickname", description = "真实玩家名（set 时指定）", required = false),
            CommandOptional(name = "scope", description = "别名作用域 群 / 个人（默认个人）", required = false),
        )
    override val description = "管理玩家别名，支持查询别人或自己时设置别名"

    private val log = KotlinLogging.logger {}

    private val repository: PlayerAliasRepository by inject(PlayerAliasRepository::class.java)

    companion object {
        const val MY_SELF = "__!!!!!!!!!MY_SELF!!!!!!!!!__"
    }

    override suspend fun listen(sender: MessageSender, command: Map<String, String>): Message? {
        val action = command["action"] ?: return helpText()
        // alias 在前（别名），nickname 在后（真实玩家名）
        val alias = if (action.trim().equals("list",true)){
            MY_SELF
        }else{
            var alias = command["alias"] ?: return helpText()
            if (alias == "我"){
                alias = MY_SELF
            }
            alias
        }
        val nickname = command["nickname"]
        val scope = if (command["scope"] == "群") AliasScope.GROUP else AliasScope.PERSONAL
        val groupId = sender.groupOpenId.toString()
        val senderId = sender.senderOpenId.toString()
        return when (action) {
            "set" -> handleSet(senderId, groupId, alias, nickname, scope)
            "del" -> handleDelete(senderId, groupId, alias, scope)
            "list" -> handleList(sender)
            else -> helpText()
        }
    }

    private fun handleSet(
        id: String,
        groupOpenId: String,
        alias: String,
        nickname: String?,
        scope: AliasScope,
    ): Message {
        if (nickname == null) {
            return "请指定真实玩家名（用法：/玩家别名 set <别名> <真实昵称> [群|个人]）".toText()
        }
        if (!NickNameUtil.isValidNickname(nickname)) {
            return "[${NickNameUtil.hideNickname(nickname)}] 该名称不合法,EternalReturn不允许使用这样的名称".toText()
        }
        // 检查该 alias 在同 scope 下是否已指向其他玩家（歧义检测）
        val existing = repository.resolveAlias(alias, groupOpenId, id)
        if (existing != null && existing != nickname) {
            repository.setAlias(alias = alias, actualNickname = nickname, scope = scope, groupId = groupOpenId, userId = id, createdBy = id)
            return "别名 「${aliasDisplay(alias)}」 原指向「$existing」，已更新为「$nickname」".toText()
        }
        repository.setAlias(alias = alias, actualNickname = nickname, scope = scope, groupId = groupOpenId, userId = id, createdBy = id)
        return "已设置别名: ${aliasDisplay(alias)} → $nickname".toText()
    }

    private fun handleDelete(id: String, groupOpenId: String, alias: String, scope: AliasScope): Message {
        val deleted = repository.deleteAlias(alias, scope, groupOpenId, id)
        return if (deleted) {
            "已删除别名: ${aliasDisplay(alias)}".toText()
        } else {
            "未找到别名: ${aliasDisplay(alias)}（请检查作用域是否正确，群 / 个人）".toText()
        }
    }

    private fun aliasDisplay(alias: String) = if (alias == MY_SELF) "自己" else alias

    private fun handleList(sender: MessageSender): Message {
        val aliases = repository.listAliases(
            groupId = sender.groupOpenId.toString(),
            userId = sender.senderOpenId.toString(),
        )

        if (aliases.isEmpty()) {
            return "暂无别名，使用 /玩家别名 set <别名> <昵称> 来添加".toText()
        }

        val sb = StringBuilder("#玩家别名列表:\n")
        val scopeIcons = mapOf(
            AliasScope.GLOBAL to "全局",
            AliasScope.GROUP to "群",
            AliasScope.PERSONAL to "个人",
        )
        for (a in aliases) {
            sb.appendLine(" - [${scopeIcons[a.scope] ?: ""}] ${aliasDisplay(a.alias)} → ${a.actualNickname}")
        }
        return QGMarkdown.create(sb.toString())
    }

    private fun helpText(): Message {
        return QGMarkdown.create(
            """## 别名命令   
            **设置** ：`/玩家别名 set <别名> <真实昵称> [群|个人]`   
            **删除** ：`/玩家别名 del <别名> [群|个人]`   
            **列表** ：`/玩家别名 list`"""
        )
    }
}
