package cn.luorenmu.onebot

import love.forte.simbot.component.qguild.message.QGMarkdown
import love.forte.simbot.message.Message
import love.forte.simbot.message.toText

/**
 * 将 OneBot 不支持的消息元素转换为兼容格式。
 *
 * @author LoMu
 * Date 2026/8/16
 */
internal fun Message.toOneBotCompatibleMessage(): Message = when (this) {
    is QGMarkdown -> markdown.content.orEmpty().markdownToPlainText().toText()
    else -> this
}

private fun String.markdownToPlainText(): String =
    replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(IMAGE_PATTERN) { result -> result.groupValues[1] }
        .replace(LINK_PATTERN) { result ->
            val (label, url) = result.destructured
            if (label == url) label else "$label ($url)"
        }
        .replace(BOLD_ASTERISK_PATTERN, "$1")
        .replace(BOLD_UNDERSCORE_PATTERN, "$1")
        .replace(ITALIC_ASTERISK_PATTERN, "$1")
        .replace(ITALIC_UNDERSCORE_PATTERN, "$1")
        .replace(STRIKETHROUGH_PATTERN, "$1")
        .replace(INLINE_CODE_PATTERN, "$1")
        .lineSequence()
        .map { line ->
            line.replace(HEADER_PATTERN, "")
                .replace(QUOTE_PATTERN, "")
                .replace(BULLET_PATTERN, "• ")
                .trimEnd()
        }
        .joinToString("\n")
        .trim('\n')

private val IMAGE_PATTERN = Regex("!\\[([^]]*)]\\([^)]*\\)")
private val LINK_PATTERN = Regex("\\[([^]]+)]\\(([^)]+)\\)")
private val BOLD_ASTERISK_PATTERN = Regex("\\*\\*([^*\\n]+)\\*\\*")
private val BOLD_UNDERSCORE_PATTERN = Regex("__([^_\\n]+)__")
private val ITALIC_ASTERISK_PATTERN = Regex("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)")
private val ITALIC_UNDERSCORE_PATTERN = Regex("(?<!_)_([^_\\n]+)_(?!_)")
private val STRIKETHROUGH_PATTERN = Regex("~~([^~\\n]+)~~")
private val INLINE_CODE_PATTERN = Regex("`([^`\\n]+)`")
private val HEADER_PATTERN = Regex("^[ \\t]{0,3}#{1,6}[ \\t]*")
private val QUOTE_PATTERN = Regex("^[ \\t]*>[ \\t]?")
private val BULLET_PATTERN = Regex("^[ \\t]*[-*+][ \\t]+")
