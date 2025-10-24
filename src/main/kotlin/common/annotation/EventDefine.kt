package cn.luorenmu.command.annotation

import kotlin.reflect.KClass

/**
 *
 * @author LoMu
 * Date 2025/10/22 23:01
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class EventDefine(
    val eventKClass: KClass<*>,
)