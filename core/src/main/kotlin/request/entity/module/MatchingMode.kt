package cn.luorenmu.request.entity.module

/**
 *
 * @author LoMu
 * Date 2025/10/26 00:30
 */
enum class MatchingMode(val value: Int, val modeName: String,val dakGGMode: String) {
    Normal(2, "匹配","NORMAL"),
    Rank(3, "排位","RANK"),
    Cobalt(6, "钴协议","Cobalt"),

    Union(8, "联盟","UNION"),

    Lonely(9, "孤狼","LONE_WOLF"),

    All(0, "全部","ALL");

    override fun toString(): String {
        return value.toString()
    }

    companion object {
        fun convert(value: Int?): MatchingMode {
            if (value == null) {
                return Rank
            }
            MatchingMode.entries.forEach { action ->
                if (action.value == value) {
                    return action
                }
            }
           return Rank
        }

        fun convert(value: String?): MatchingMode {
            if (value == null) {
                return Rank
            }
            MatchingMode.entries.forEach { action ->
                if (action.modeName == value) {
                    return action
                }
            }
            if (value.toIntOrNull() != null) {
                return convert(value.toInt())
            }
            return Rank
        }

    }
}