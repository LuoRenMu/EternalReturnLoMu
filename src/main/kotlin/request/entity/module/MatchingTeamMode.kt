package cn.luorenmu.request.entity.module

/**
 *
 * @author LoMu
 * Date 2025/10/26 00:24
 */
enum class MatchingTeamMode(val value: Int) {
    Lonely(1),
    Double(2),
    SQUAD(3),
    Cobalt(4),

    All(0);

    override fun toString(): String {
        return value.toString()
    }

    companion object {
        fun convert(value: Int): MatchingTeamMode {
            return when (value) {
                Lonely.value -> Lonely
                Double.value -> Double
                SQUAD.value -> SQUAD
                Cobalt.value -> Cobalt
                else -> All
            }
        }
    }
}