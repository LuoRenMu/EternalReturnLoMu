package cn.luorenmu.repository.entity

import java.time.LocalDateTime

/**
 * 官方新闻记录实体
 *
 * 保存从永恒轮回官方新闻 API 拉取、经 HTML 解析与 AI 分类处理后的结果。
 * 以 [articleId] 唯一标识，用于幂等去重。
 *
 * @author LoMu
 * Date 2026/8/9
 */
data class EternalReturnNewsRecord(
    val id: Long = 0,
    /** 官方文章 ID，唯一，用于去重 */
    val articleId: Int,
    val title: String,
    /** 文章缩略图 URL */
    val thumbnailUrl: String? = null,
    /** 官方文章创建时间（已转换为本地时区） */
    val createdAt: LocalDateTime? = null,
    /** 文章正文解析出的纯文本 */
    val contentText: String = "",
    val eventStartTime: LocalDateTime? = null,
    val eventEndTime: LocalDateTime? = null,
    val isGameActivity: Boolean = false,
    val isRedemptionCode: Boolean = false,
    val code: String? = null,
    val reward: String? = null,
    val note: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    /** 本任务处理入库时间 */
    val processedAt: LocalDateTime = LocalDateTime.now(),
)
