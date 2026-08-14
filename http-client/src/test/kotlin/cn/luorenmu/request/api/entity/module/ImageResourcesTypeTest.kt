package cn.luorenmu.request.api.entity.module

import kotlin.test.Test
import kotlin.test.assertEquals

class ImageResourcesTypeTest {
    @Test
    fun `banner path uses queried game season`() {
        assertEquals(
            "/resources/images/bg/bg-landing-search-v9.jpg",
            ImageResourcesType.bannerPathForSeason(38),
        )
        assertEquals(
            "/resources/images/bg/bg-landing-search-v12.jpg",
            ImageResourcesType.bannerPathForSeason(41),
        )
        assertEquals(41, ImageResourcesType.resolveBannerSeasonId(listOf(0, 0), 41))
        assertEquals(38, ImageResourcesType.resolveBannerSeasonId(listOf(0, 38), 41))
    }
}
