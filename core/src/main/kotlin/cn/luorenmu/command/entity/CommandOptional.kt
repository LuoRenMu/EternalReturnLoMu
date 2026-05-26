package cn.luorenmu.command.entity

/**
 *
 * @author LoMu
 * Date 2026/5/24 17:46
 */
data class CommandOptional(
    val required: Boolean = true,
    val name: String,
    val description: String = "",
)
