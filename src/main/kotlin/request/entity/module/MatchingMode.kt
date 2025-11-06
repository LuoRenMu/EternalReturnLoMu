package cn.luorenmu.request.entity.module

/**
 *
 * @author LoMu
 * Date 2025/10/26 00:30
 */
enum class MatchingMode(val value: Int, val modeName: String) {
     Normal (2, "匹配"),
     Rank (3, "排位"),
     Cobalt (6, "钴协议"),

     Union (8, "联盟"),

     Lonely(9, "孤狼"),

     All (0, "未知");

    override fun toString(): String {
        return value.toString()
    }

    companion object {
        fun convert(value: Int): MatchingMode {
            return when (value) {
                Normal.value -> Normal
                Rank.value -> Rank
                Cobalt.value -> Cobalt
                Union.value -> Union
                Lonely.value -> Lonely
                else -> All
            }
        }
    }
}