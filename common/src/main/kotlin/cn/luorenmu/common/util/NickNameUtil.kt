package cn.luorenmu.common.util

/**
 *
 * @author LoMu
 * Date 2026/6/10 17:51
 */
object NickNameUtil {
    fun isValidNickname(nickname: String): Boolean {
        if (nickname.isBlank()) return false

        fun isAsciiAlnum(c: Char) = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9'
        fun isCJKChar(c: Char) = when (Character.UnicodeScript.of(c.code)) {
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HANGUL -> true
            else -> false
        }

        val isEnglishOnly = nickname.all { isAsciiAlnum(it) }
        if (isEnglishOnly) return nickname.length in 3..16

        val isCJKOnly = nickname.all { isCJKChar(it) }
        if (isCJKOnly) return nickname.length in 2..8

        // 混合：每个字符必须是英文/数字或CJK
        val isMixed = nickname.all { isAsciiAlnum(it) || isCJKChar(it) }
        return isMixed && nickname.length in 2..16
    }

    fun hideNickname(nickname: String): String {
        val length = nickname.length
        return if (length < 3) {
            nickname.replace(nickname.substring(1, length), " * ")
        } else {
            nickname.replace(nickname.substring(1, length - 1), " * ".repeat(length - 2))
        }
    }
}