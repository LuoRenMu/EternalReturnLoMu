package cn.luorenmu.nutdraw.render

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
data class FontFallbackConfig(
    val families: List<String?> = listOf(
        "Microsoft YaHei", "Microsoft JhengHei", "Yu Gothic UI", "Meiryo", "MS Gothic",
        "Malgun Gothic", "Apple SD Gothic Neo", "Noto Sans CJK SC", "Noto Sans CJK JP",
        "Noto Sans CJK KR", "Noto Sans SC", "Noto Sans JP", "Noto Sans KR",
        "Arial Unicode MS", "Segoe UI Emoji", "Arial",
    ),
    val languages: List<String> = listOf("zh-CN", "zh-TW", "ja-JP", "ko-KR", "en-US"),
    val finalFallback: String = "Arial",
)
