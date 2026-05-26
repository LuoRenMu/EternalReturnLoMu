package cn.luorenmu.common.util

import org.reflections.Reflections

/**
 *
 * @author LoMu
 * Date 2025/10/24 13:03
 */
object ReflectionUtil {
    fun getAnnotatedClassInPackage(packageName: String, clazz: Class<out Annotation>): Set<Class<*>> {
        val reflections = Reflections(packageName)
        return reflections.getTypesAnnotatedWith(clazz)
    }
    fun <T> getSubTypesOf(packageName: String, clazz: Class<in T>): Set<Class<*>>  {
        val reflections = Reflections(packageName)
        return reflections.getSubTypesOf(clazz)
    }
}