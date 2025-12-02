package cn.luorenmu.request.api.entity.module

import love.forte.simbot.common.time.TimeUnit

/**
 *
 * @author LoMu
 * Date 2025/11/21 13:36
 */
enum class CacheTime(val duration: Long, val unit: TimeUnit) {
    FIVE_MINUTES(5, TimeUnit.MINUTES),
    ONE_HOUR(1, TimeUnit.HOURS),
    ONE_DAY(5, TimeUnit.DAYS),
    ONE_WEEK(7, TimeUnit.DAYS),
    NULL(0, TimeUnit.MILLISECONDS),
}