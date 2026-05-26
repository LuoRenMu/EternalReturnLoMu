package cn.luorenmu.request.entity.module

/**
 *
 * @author LoMu
 * Date 2026/5/23 20:14
 */
enum class DakGGRank(val value: String, val shortName: String) {
    DIAMOND_PLUS("diamond_plus","灭钻"),
    MITHRIL_PLUS("mithril_plus","无暇"),
    METEORITE_PLUS("meteorite_plus","星陨"),
    PLATINUM_PLUS("platinum_plus","修罗"),
    GOLD("gold","黄金"),
    SILVER("silver","白银"),
    BRONZE("bronze","青铜"),
    IRON("iron","铁阎"),
    IN_1000("in1000","in1000");

    companion object{
        fun convert(s:String):DakGGRank{
            entries.forEach {
                if (it.shortName == s) {
                    return it
                }
            }
            return DIAMOND_PLUS
        }
    }
}