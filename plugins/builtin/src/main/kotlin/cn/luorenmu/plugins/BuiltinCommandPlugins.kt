package cn.luorenmu.plugins

import cn.luorenmu.command.plugin.CommandPluginFactory
import cn.luorenmu.plugins.character.CharacterPlugin
import cn.luorenmu.plugins.news.NewsPlugin
import cn.luorenmu.plugins.player.PlayerPlugin
import cn.luorenmu.plugins.tier.TierPlugin

object BuiltinCommandPlugins {
    val factories = listOf(
        CommandPluginFactory(::CharacterPlugin),
        CommandPluginFactory(::PlayerPlugin),
        CommandPluginFactory(::TierPlugin),
        CommandPluginFactory(::NewsPlugin),
    )

    /** IDE/Gradle development bootstrap: makes every source plugin available without copying jars. */
    fun installAll() {
        cn.luorenmu.command.plugin.CommandPlugins.configureBuiltins(factories)
    }
}
