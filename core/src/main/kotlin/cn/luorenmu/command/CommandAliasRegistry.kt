package cn.luorenmu.command

import cn.luorenmu.common.annotation.BotCommand

internal object CommandAliasRegistry {
    fun <T> register(target: MutableMap<String, T>, command: BotCommand, value: T) {
        (listOf(command.alias) + command.aliases)
            .distinct()
            .forEach { alias ->
                require(alias.isNotBlank()) { "命令别名不能为空" }
                require(!alias.startsWith('/')) { "命令别名不应包含开头的 /: $alias" }
                require(alias.none(Char::isWhitespace)) { "命令别名不能包含空白字符: $alias" }
                require(target.putIfAbsent(alias, value) == null) { "命令别名重复: $alias" }
            }
    }
}
