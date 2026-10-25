package cn.luorenmu.plugins.character

import cn.luorenmu.exception.MessageReplyException
import cn.luorenmu.request.entity.module.DakGGRank
import cn.luorenmu.service.entity.CharacterDetail
import love.forte.simbot.component.qguild.message.QGKeyboards
import love.forte.simbot.component.qguild.message.QGMarkdown
import love.forte.simbot.message.Message
import love.forte.simbot.message.Messages
import love.forte.simbot.message.toText
import love.forte.simbot.qguild.model.MessageKeyboard

internal data class CharacterDetailOptions(
    val mode: String = "排位",
    val rank: DakGGRank = DakGGRank.DIAMOND_PLUS,
    val weapon: String? = null,
) {
    val teamMode get() = if (mode == "钴协议") "COBALT" else "SQUAD"
    val matchingMode get() = if (mode == "钴协议") "COBALT" else "RANK"
    val tierKey get() = if (mode == "钴协议") null else rank.value

    fun command(characterId: Long, weaponId: Long): String =
        "/查询角色 $characterId $mode ${if (tierKey != null) "${rank.shortName} " else ""}$weaponId"

    companion object {
        fun parse(command: Map<String, String>): CharacterDetailOptions {
            var mode: String? = null
            var rank: DakGGRank? = null
            var weapon: String? = null
            for (token in listOf("mode", "tier", "weapon").mapNotNull { command[it]?.trim()?.takeIf(String::isNotEmpty) }) {
                val tier = DakGGRank.entries.firstOrNull {
                    it.shortName.equals(token, true) || it.value.equals(token, true)
                }
                when {
                    token in setOf("排位", "钴协议") -> {
                        if (mode != null) throw MessageReplyException("请只指定一个模式")
                        mode = token
                    }
                    tier != null -> {
                        if (rank != null) throw MessageReplyException("请只指定一个段位")
                        rank = tier
                    }
                    else -> {
                        if (weapon != null) throw MessageReplyException("参数无法识别：$token。示例：/查询角色 娜汀 无暇 弓")
                        weapon = token
                    }
                }
            }
            return CharacterDetailOptions(mode ?: "排位", rank ?: DakGGRank.DIAMOND_PLUS, weapon)
        }
    }
}

internal fun CharacterDetail.selectWeapon(query: String?): CharacterDetail {
    if (query == null) return this
    val stats = analysis ?: throw MessageReplyException("当前模式或段位暂无角色数据")
    val selected = stats.weapons.firstOrNull { it.weapon == query || it.weaponId.toString() == query }
        ?: throw MessageReplyException("该模式或段位没有武器「$query」的数据，可选：${stats.weapons.joinToString("、") { it.weapon }}")
    return copy(analysis = stats.copy(
        characterTier = selected.tier,
        weapons = listOf(selected) + stats.weapons.filterNot { it.weaponId == selected.weaponId },
    ))
}

internal fun characterWeaponChoices(detail: CharacterDetail, options: CharacterDetailOptions, qq: Boolean): Message {
    val weapons = detail.analysis?.weapons.orEmpty()
    val heading = "${detail.name} · ${options.mode}${if (options.tierKey != null) " · ${options.rank.shortName}" else ""}：请选择武器"
    if (!qq) return (heading + "\n" + weapons.joinToString("\n") {
        "${it.weapon}：${options.command(detail.id, it.weaponId)}"
    }).toText()
    return Messages.of(
        QGMarkdown.create(heading),
        QGKeyboards.create(weapons.map { weapon ->
            MessageKeyboard.builder("character-weapon-${detail.id}-${weapon.weaponId}")
                .renderData(weapon.weapon, weapon.weapon, 1)
                .action(type = 2, permission = MessageKeyboard.ActionPermission.AllAccessible,
                    data = options.command(detail.id, weapon.weaponId), enter = false, reply = false,
                    unsupportTips = "请发送 ${options.command(detail.id, weapon.weaponId)}")
                .build()
        }),
    )
}
