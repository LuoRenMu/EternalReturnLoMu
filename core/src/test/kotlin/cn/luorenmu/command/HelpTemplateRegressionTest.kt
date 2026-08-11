package cn.luorenmu.command

import cn.luorenmu.command.entity.CommandHelp
import cn.luorenmu.command.entity.CommandOptional
import cn.luorenmu.nutdraw.layout.FlexLayoutEngine
import cn.luorenmu.nutdraw.layout.LayoutBox
import cn.luorenmu.command.template.HelpTemplate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HelpTemplateRegressionTest {
    @Test
    fun `cartoon help layout stays centered and inside every parent`() {
        val document = HelpTemplate().build(
            CommandHelp(
                listOf(
                    CommandHelp.CommandHelpItem("help", "查看全部命令", "/help", emptyList()),
                    CommandHelp.CommandHelpItem(
                        "player",
                        "查询玩家段位与最近比赛",
                        "/player LuoMu 排位",
                        listOf(
                            CommandOptional("nickname", "玩家昵称", true),
                            CommandOptional("mode", "匹配模式", false),
                            CommandOptional("season", "目标赛季", false),
                        ),
                    ),
                )
            )
        )
        val layout = FlexLayoutEngine().layout(document.root, document.width.toFloat(), document.height.toFloat())

        layout.assertChildrenContained()
        val header = assertNotNull(layout.findById("help-header"))
        val title = assertNotNull(layout.findById("help-title"))
        val headerCenter = (header.bounds.left + header.bounds.right) / 2f
        val titleCenter = (title.bounds.left + title.bounds.right) / 2f
        assertTrue(abs(headerCenter - titleCenter) < 0.5f)
        val firstCard = assertNotNull(layout.findById("command-card-0"))
        val secondCard = assertNotNull(layout.findById("command-card-1"))
        assertTrue(abs(firstCard.bounds.top - secondCard.bounds.top) < 0.5f)
        assertTrue(secondCard.bounds.left > firstCard.bounds.right)

        listOf("command-heading-0", "command-description-0", "command-example-0", "command-heading-1", "command-description-1", "command-example-1")
            .forEach { assertNotNull(layout.findById(it)) }
    }

    private fun LayoutBox.assertChildrenContained() {
        children.forEach { child ->
            assertTrue(child.bounds.left >= bounds.left - 0.5f, "${child.node.id} exceeds left of ${node.id}")
            assertTrue(child.bounds.top >= bounds.top - 0.5f, "${child.node.id} exceeds top of ${node.id}")
            assertTrue(child.bounds.right <= bounds.right + 0.5f, "${child.node.id} exceeds right of ${node.id}")
            assertTrue(child.bounds.bottom <= bounds.bottom + 0.5f, "${child.node.id} exceeds bottom of ${node.id}")
            child.assertChildrenContained()
        }
    }

    private fun LayoutBox.findById(id: String): LayoutBox? =
        takeIf { node.id == id } ?: children.firstNotNullOfOrNull { it.findById(id) }
}
