package cn.luorenmu.command

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
internal data class CommandTextMatch<T>(
    val value: T,
    val arguments: String,
)

internal object CommandTextParser {
    fun <T> find(
        input: String,
        commands: Map<String, T>,
        acceptsAttachedArgument: (T) -> Boolean,
    ): CommandTextMatch<T>? {
        val normalized = input.trim().removePrefix("/")
        if (normalized.isBlank()) return null

        return commands.entries
            .asSequence()
            .sortedByDescending { it.key.length }
            .mapNotNull { (alias, value) ->
                if (!normalized.startsWith(alias)) return@mapNotNull null
                val remainder = normalized.substring(alias.length)
                when {
                    remainder.isEmpty() -> CommandTextMatch(value, "")
                    remainder.first().isWhitespace() -> CommandTextMatch(value, remainder.trim())
                    acceptsAttachedArgument(value) -> CommandTextMatch(value, remainder.trim())
                    else -> null
                }
            }
            .firstOrNull()
    }

    fun parseArguments(template: String, arguments: String): Map<String, String> {
        if (template.isBlank() || arguments.isBlank()) return emptyMap()
        val keys = ARGUMENT.findAll(template).map { it.groupValues[1] }.toList()
        val values = arguments.trim().split(WHITESPACE)
        return keys.zip(values).toMap()
    }

    private val ARGUMENT = Regex("<(.*?)>")
    private val WHITESPACE = Regex("\\s+")
}
