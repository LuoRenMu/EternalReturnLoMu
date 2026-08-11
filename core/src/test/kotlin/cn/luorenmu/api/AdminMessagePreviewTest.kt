package cn.luorenmu.api

import cn.luorenmu.common.util.PathUtils
import love.forte.simbot.message.OfflineImage
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminMessagePreviewTest {
    @Test
    fun offlineResourceImageUsesPublicResourcesUrl() {
        val image = OfflineImage.fileOfflineImage(
            PathUtils.resourcesPathResolve("render", "player", "神圣审判-3.png").toString()
        )

        val preview = MessagePreviewElement.from(image)

        assertEquals("image", preview.type)
        assertEquals("/resources/render/player/神圣审判-3.png", preview.imageUrl)
    }
}
