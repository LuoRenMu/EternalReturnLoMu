package cn.luorenmu.command.plugin

import kotlinx.serialization.Serializable

@Serializable
data class CommandPluginView(
    val id: String,
    val name: String,
    val version: String,
    val enabled: Boolean,
    val external: Boolean,
    val commands: List<String>,
    val source: String,
    val disabledReply: String,
)
