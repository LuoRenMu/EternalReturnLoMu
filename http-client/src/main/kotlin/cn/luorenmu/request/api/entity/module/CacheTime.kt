package cn.luorenmu.request.api.entity.module

import java.util.concurrent.TimeUnit

/**
 *
 * @author LoMu
 * Date 2025/11/21 13:36
 */
enum class CacheTime(val duration: Long, val unit: TimeUnit) {
    MINUTES(5, TimeUnit.MINUTES),
    HOUR(1, TimeUnit.HOURS),
    DAY(5, TimeUnit.DAYS),
    WEEK(7, TimeUnit.DAYS),
    NULL(0, TimeUnit.MILLISECONDS),
}