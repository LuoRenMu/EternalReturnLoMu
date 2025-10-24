package cn.luorenmu.common.annotation

/**
 *
 * @author LoMu
 * Date 2025/10/24 14:05
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class CommandFilter(
    val value: String
)
