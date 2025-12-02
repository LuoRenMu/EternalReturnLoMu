package cn.luorenmu.request.entity.module

/**
 *
 * @author LoMu
 * Date 2025/11/1 00:03
 */
enum class DakGGServerName(val value: String, val alias: Array<String>) {
    Asia("seoul", arrayOf("亚1", "亚一", "国际服")),
    Asia2("asia2", arrayOf("亚2", "亚二", "国服")),
    Asia3("asia3", arrayOf("亚3", "亚三")),
    NA("ohio", arrayOf("美1", "美一", "美服")),
    EU("frankfurt", arrayOf("欧1", "欧一", "欧服")),
    SA("saopaulo", arrayOf("南1", "南一", "南美")),
    Global("global", emptyArray());

    companion object {
        fun convert(value: String?): DakGGServerName {
            if (value.isNullOrBlank()) return Asia
            return entries.find { it.value.equals(value, ignoreCase = true) }
                ?: entries.find { it.alias.any { alias -> alias.equals(value, ignoreCase = true) } } ?: Asia
        }
    }
}

