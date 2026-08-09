package cn.luorenmu.command

import cn.luorenmu.command.entity.RedemptionCodeActivityPage
import cn.luorenmu.render.FreemarkerRenderer
import kotlin.test.Test
import kotlin.test.assertContains

class RedemptionCodeActivityTemplateTest {

    @Test
    fun rendersThumbnailUrl() {
        val html = FreemarkerRenderer.render(
            "redemption_code_activity.ftl",
            RedemptionCodeActivityPage(
                generatedDate = "2026-08-09",
                items = listOf(
                    RedemptionCodeActivityPage.Item(
                        title = "测试活动",
                        code = "ABC123",
                        reward = "测试奖励",
                        note = "测试说明",
                        period = "2026-08-01 至 2026-08-10",
                        status = "有效中",
                        thumbnailUrl = "https://example.com/banner.png",
                    )
                ),
            )
        )

        assertContains(html, "测试活动")
        assertContains(html, "ABC123")
        assertContains(html, """src="https://example.com/banner.png"""")
        assertContains(html, "游戏活动")
    }

    @Test
    fun rendersActivityWithoutCode() {
        val html = FreemarkerRenderer.render(
            "redemption_code_activity.ftl",
            RedemptionCodeActivityPage(
                generatedDate = "2026-08-09",
                items = listOf(
                    RedemptionCodeActivityPage.Item(
                        title = "网页活动",
                        code = null,
                        reward = "前往活动页面查看详情",
                        note = "完成任务领取奖励",
                        period = "2026-08-01 至 2026-08-10",
                        status = "有效中",
                        thumbnailUrl = "https://example.com/activity.png",
                    )
                ),
            )
        )

        assertContains(html, "网页活动")
        assertContains(html, "前往活动页面查看详情")
        assertContains(html, """src="https://example.com/activity.png"""")
    }
}
