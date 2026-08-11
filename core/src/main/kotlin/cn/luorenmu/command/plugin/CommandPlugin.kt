package cn.luorenmu.command.plugin

import cn.luorenmu.command.CommandEvent

/** A replaceable command Module. Implementations should keep command-specific assets in the same jar. */
interface CommandPlugin {
    val id: String
    val name: String
    val version: String
    val commands: List<CommandEvent>

    fun onEnable() = Unit
    fun onDisable() = Unit
}

fun interface CommandPluginFactory {
    fun create(): CommandPlugin
}
