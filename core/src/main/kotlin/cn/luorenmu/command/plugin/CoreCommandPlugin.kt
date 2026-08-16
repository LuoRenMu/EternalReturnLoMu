package cn.luorenmu.command.plugin

import cn.luorenmu.command.HelpCommand

/**
 * Core commands stay available while feature Modules are disabled or replaced.
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
internal class CoreCommandPlugin : CommandPlugin {
    override val id = "core"
    override val name = "核心命令"
    override val version = "3.0.0"
    override val commands = listOf(HelpCommand())
}
