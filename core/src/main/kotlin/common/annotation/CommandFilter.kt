package cn.luorenmu.common.annotation

import java.util.UUID

/**
 *
 * @author LoMu
 * Date 2025/10/24 14:05
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class CommandFilter(
    val id: String ,
    val alias: Array<String> = [],
    val value: String
)
