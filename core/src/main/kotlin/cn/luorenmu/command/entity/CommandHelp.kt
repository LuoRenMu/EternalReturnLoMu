package cn.luorenmu.command.entity

/**
 *
 * @author LoMu
 * Date 2026/5/24 18:14
 */
data class CommandHelp(
    val helps: List<CommandHelpItem>,
    ){
    data class CommandHelpItem(
        val name: String,
        val description: String,
        val example: String,
        val optionals: List<CommandOptional>
    )
}
