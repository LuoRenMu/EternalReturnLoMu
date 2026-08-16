package cn.luorenmu.command.entity

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
data class RedemptionCodeActivityPage(
    val generatedDate: String,
    val items: List<Item>,
) {
    data class Item(
        val title: String,
        val code: String?,
        val reward: String,
        val note: String,
        val period: String,
        val status: String,
        val thumbnailUrl: String?,
    )
}
