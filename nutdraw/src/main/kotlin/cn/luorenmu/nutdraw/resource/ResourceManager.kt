package cn.luorenmu.nutdraw.resource

import cn.luorenmu.nutdraw.render.CjkFontResolver
import cn.luorenmu.nutdraw.render.ImageLoader

/**
 * Shared resource boundary inspired by Shinobu's ResourceManager.
 * It owns caches and font resolution for a renderer instead of letting templates load resources.
 */
class ResourceManager(
    val images: ImageLoader = ImageLoader(),
    val fonts: CjkFontResolver = CjkFontResolver(),
)
