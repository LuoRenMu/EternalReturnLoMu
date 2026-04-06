package cn.luorenmu.common.util

import java.nio.file.Path
/**
 *
 * @author LoMu
 * Date 2026/4/5 23:32
 */
object ResourceCheckUtil {

    /**
     * 资源缓存Map
     * Key: 资源路径
     * Value: 资源状态 (0=存在但无效, >0=存在且有效)
     * null = 不存在(不存储在map中)
     */
    private val fileMap = mutableMapOf<Path, Byte>()

    fun checkResource(resource: Path): Boolean {
        // 先从缓存中查找
        fileMap[resource]?.let {
            return it > 0
        }

        val file = resource.toFile()

        if (file.exists() && file.length() > 0) {
            fileMap[resource] = 1 // 标记为存在且有效
            return true
        }
        
        return false
    }
    
    /**
     * 标记资源为无效状态
     * @param resource 资源路径
     */
    fun markResourceInvalid(resource: Path) {
        if (fileMap.containsKey(resource)) {
            fileMap[resource] = 0 // 标记为存在但无效 图片可能下载失败 或者目标资源不是图片(数据api中可能存在但无权限访问)
        }
    }
    
    /**
     * 标记资源为有效状态
     * @param resource 资源路径
     */
    fun markResourceValid(resource: Path) {
        if (fileMap.containsKey(resource)) {
            fileMap[resource] = 1
        } else if (resource.toFile().exists()) {
            fileMap[resource] = 1
        }
    }
    
    /**
     * 移除资源缓存（用于文件删除后清理缓存）
     * @param resource 资源路径
     */
    fun removeResource(resource: Path) {
        fileMap.remove(resource)
    }
    
    /**
     * 获取缓存大小
     */
    fun getCacheSize(): Int = fileMap.size
    
    /**
     * 清空所有缓存
     */
    fun clearCache() {
        fileMap.clear()
    }
}