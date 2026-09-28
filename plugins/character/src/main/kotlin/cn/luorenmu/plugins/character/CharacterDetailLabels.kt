package cn.luorenmu.plugins.character

import java.util.Locale

/** Display labels only; unknown upstream roles remain visible instead of being misclassified. */
internal fun characterRoleLabels(roles: List<String>): String = roles.mapNotNull { raw ->
    val role = raw.trim()
    when (role.lowercase(Locale.ROOT)) {
        "", "none" -> null
        "warrior", "fighter", "bruiser" -> "战士"
        "assassin" -> "刺客"
        "tank", "tanker" -> "坦克"
        "mage" -> "法师"
        "marksman", "adc" -> "射手"
        "support", "supporter" -> "辅助"
        else -> role
    }
}.distinct().joinToString(" / ")

internal fun characterRankingLabel(rank: Long, total: Long): String =
    if (rank > 0 && total >= rank) "#$rank / $total" else "暂无排名"
