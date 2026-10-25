package cn.luorenmu.plugins.player

/** Adds a popularity aside to the existing performance rating after the 50th lookup. */
internal fun playerRateWithQueryCount(rate: String, totalQueries: Long): String =
    if (totalQueries > 50) "$rate 该玩家已经被查询 $totalQueries 次，这是群宠吗喵？" else rate
