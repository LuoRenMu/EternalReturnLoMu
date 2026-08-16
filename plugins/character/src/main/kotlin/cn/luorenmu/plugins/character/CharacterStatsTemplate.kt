package cn.luorenmu.plugins.character

import cn.luorenmu.nutdraw.css.AlignItems
import cn.luorenmu.nutdraw.css.Border
import cn.luorenmu.nutdraw.css.CssStyle
import cn.luorenmu.nutdraw.css.Edges
import cn.luorenmu.nutdraw.css.FlexDirection
import cn.luorenmu.nutdraw.css.FlexWrap
import cn.luorenmu.nutdraw.css.JustifyContent
import cn.luorenmu.nutdraw.css.ObjectFit
import cn.luorenmu.nutdraw.css.Position
import cn.luorenmu.nutdraw.css.TextAlign
import cn.luorenmu.nutdraw.css.VerticalAlign
import cn.luorenmu.nutdraw.css.percent
import cn.luorenmu.nutdraw.css.px
import cn.luorenmu.nutdraw.dom.ElementBuilder
import cn.luorenmu.nutdraw.dom.document
import cn.luorenmu.nutdraw.template.ImageTemplate
import cn.luorenmu.nutdraw.template.TemplateDocument
import cn.luorenmu.service.entity.CharacterStats
import org.jetbrains.skia.Color

/**
 * 紧凑展示全部英雄武器组合的角色数据模板。
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
class CharacterStatsTemplate : ImageTemplate<CharacterStats> {
    override fun build(data: CharacterStats): TemplateDocument {
        val rowCount = (data.players.size + COLUMN_COUNT - 1) / COLUMN_COUNT
        val gridHeight = if (rowCount == 0) 0f else rowCount * CARD_HEIGHT + (rowCount - 1) * GRID_GAP
        val height = (PAGE_PADDING * 2 + HEADER_HEIGHT + HEADER_GAP + gridHeight).toInt()
        val base = data.httpServer

        return TemplateDocument(
            PAGE_WIDTH,
            height,
            document(
                CssStyle(
                    width = px(PAGE_WIDTH),
                    height = px(height),
                    padding = Edges(PAGE_PADDING),
                    gap = HEADER_GAP,
                    background = Color.makeRGB(242, 244, 249),
                ),
                id = "character-stats",
            ) {
                element(
                    CssStyle(
                        direction = FlexDirection.ROW,
                        width = percent(100),
                        height = px(HEADER_HEIGHT),
                        padding = Edges(10f, 16f),
                        alignItems = AlignItems.CENTER,
                        justifyContent = JustifyContent.SPACE_BETWEEN,
                        background = Color.WHITE,
                        border = Border(1f, Color.makeRGB(224, 228, 238)),
                        borderRadius = 12f,
                    ),
                    id = "stats-header",
                ) {
                    text(
                        "角色数据 · ${data.tier}",
                        CssStyle(
                            width = px(360),
                            height = px(30),
                            fontSize = 22f,
                            fontWeight = 800,
                            color = Color.makeRGB(32, 36, 48),
                            verticalAlign = VerticalAlign.CENTER,
                        ),
                    )
                    text(
                        "${data.players.size} 个英雄武器组合",
                        CssStyle(
                            width = px(220),
                            height = px(24),
                            fontSize = 12f,
                            color = Color.makeRGB(112, 120, 144),
                            textAlign = TextAlign.END,
                            verticalAlign = VerticalAlign.CENTER,
                        ),
                    )
                }

                element(
                    CssStyle(
                        direction = FlexDirection.ROW,
                        wrap = FlexWrap.WRAP,
                        width = percent(100),
                        height = px(gridHeight),
                        gap = GRID_GAP,
                        alignItems = AlignItems.START,
                    ),
                    id = "stats-grid",
                ) {
                    data.players.forEachIndexed { index, player ->
                        characterWeaponCard(index, player, base)
                    }
                }
            },
        )
    }

    private fun ElementBuilder.characterWeaponCard(
        index: Int,
        player: CharacterStats.CharacterStatsPlayer,
        base: String,
    ) {
        element(
            CssStyle(
                width = px(CARD_WIDTH),
                height = px(CARD_HEIGHT),
                padding = Edges(7f),
                gap = 3f,
                alignItems = AlignItems.CENTER,
                background = Color.WHITE,
                border = Border(1f, Color.makeRGB(224, 228, 238)),
                borderRadius = 12f,
            ),
            id = "stats-card-$index",
        ) {
            element(
                CssStyle(width = px(66), height = px(66), position = Position.STATIC),
                id = "stats-portrait-$index",
            ) {
                image(
                    player.characterImgUrl.resolve(base),
                    CssStyle(
                        width = px(66),
                        height = px(66),
                        border = Border(1f, Color.makeRGB(209, 214, 226)),
                        borderRadius = 33f,
                        objectFit = ObjectFit.COVER,
                    ),
                    id = "stats-character-$index",
                )
                element(
                    CssStyle(
                        width = px(TIER_ICON_SIZE),
                        height = px(TIER_ICON_SIZE),
                        position = Position.ABSOLUTE,
                        left = 0f,
                        top = 0f,
                    ),
                ) {
                    characterTierIcon(
                        characterTierIconUrl(player.tier).resolve(base),
                        TIER_ICON_SIZE,
                        "stats-tier-$index",
                    )
                }
                element(
                    CssStyle(
                        width = px(27),
                        height = px(27),
                        padding = Edges(3f),
                        position = Position.ABSOLUTE,
                        right = 0f,
                        bottom = 0f,
                        background = Color.makeRGB(38, 42, 52),
                        border = Border(2f, Color.WHITE),
                        borderRadius = 14f,
                    ),
                    id = "stats-weapon-shell-$index",
                ) {
                    image(
                        player.weaponImgUrl.resolve(base),
                        CssStyle(width = percent(100), height = percent(100), objectFit = ObjectFit.CONTAIN),
                        id = "stats-weapon-$index",
                    )
                }
            }
            text(
                "选择率 ${player.pickRate}",
                metricTextStyle(Color.makeRGB(70, 77, 94)),
                id = "stats-pick-rate-$index",
            )
            text(
                "${player.playCount} 场",
                metricTextStyle(Color.makeRGB(132, 139, 158)),
                id = "stats-play-count-$index",
            )
        }
    }

    private fun metricTextStyle(color: Int) = CssStyle(
        width = percent(100),
        height = px(18),
        fontSize = 11f,
        color = color,
        textAlign = TextAlign.CENTER,
        verticalAlign = VerticalAlign.CENTER,
    )

    private fun String?.resolve(base: String): String? = this
        ?.takeIf(String::isNotBlank)
        ?.let { if (it.startsWith("http")) it else base.trimEnd('/') + "/" + it.trimStart('/') }

    companion object {
        private const val PAGE_WIDTH = 1250
        private const val PAGE_PADDING = 16f
        private const val HEADER_HEIGHT = 58f
        private const val HEADER_GAP = 12f
        private const val COLUMN_COUNT = 10
        private const val CARD_WIDTH = 114f
        private const val CARD_HEIGHT = 126f
        private const val GRID_GAP = 8f
        private const val TIER_ICON_SIZE = 28f
    }
}
