package cn.luorenmu.nutdraw.render

data class FontFallbackConfig(
    val families: Array<String?> = arrayOf(
        "Microsoft YaHei", "Microsoft JhengHei", "Yu Gothic UI", "Meiryo", "MS Gothic",
        "Malgun Gothic", "Apple SD Gothic Neo", "Noto Sans CJK SC", "Noto Sans CJK JP",
        "Noto Sans CJK KR", "Noto Sans SC", "Noto Sans JP", "Noto Sans KR",
        "Arial Unicode MS", "Segoe UI Emoji", "Arial",
    ),
    val languages: Array<String> = arrayOf("zh-CN", "zh-TW", "ja-JP", "ko-KR", "en-US"),
    val finalFallback: String = "Arial",
)
