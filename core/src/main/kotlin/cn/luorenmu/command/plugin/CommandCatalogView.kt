package cn.luorenmu.command.plugin

import kotlinx.serialization.Serializable

/**
 * 管理端 Debug 可用命令目录项。
 *
 * @author LoMu
 * Date 2026/8/16
 */
@Serializable
data class CommandCatalogView(
    val name: String,
    val alias: String,
    val parameters: String,
    val description: String,
    val example: String,
)
