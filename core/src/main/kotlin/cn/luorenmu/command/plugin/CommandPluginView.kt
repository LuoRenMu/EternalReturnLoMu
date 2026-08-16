package cn.luorenmu.command.plugin

import kotlinx.serialization.Serializable

@Serializable
/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
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
