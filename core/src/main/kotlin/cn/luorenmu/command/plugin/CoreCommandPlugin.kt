package cn.luorenmu.command.plugin

import cn.luorenmu.command.HelpCommand

/** Core commands stay available while feature Modules are disabled or replaced. */
internal class CoreCommandPlugin : CommandPlugin {
    override val id = "core"
    override val name = "核心命令"
    override val version = "1.0.0"
    override val commands = listOf(HelpCommand())
}
