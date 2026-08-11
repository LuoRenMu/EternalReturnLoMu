package cn.luorenmu.plugins

import cn.luorenmu.service.entity.CharacterDetail
import java.nio.file.Files
import java.nio.file.Path

internal object RioCharacterDetailSample {
    private val root: Path by lazy {
        generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .first { Files.isDirectory(it.resolve("resources/images")) }
    }

    fun create(): CharacterDetail {
        val skills = listOf(
            skill(1024100, "Q", "和弓"),
            skill(1024200, "W", "箭雨"),
            skill(1024300, "E", "弓术"),
            skill(1024400, "R", "连射"),
            skill(1024500, "T", "会心一击"),
        )
        val items = listOf(
            item(112502, "血色弓"), item(202526, "龙服"), item(201526, "王冠"), item(204511, "雷达"), item(205504, "红鞋"),
            item(112501, "长弓"), item(202523, "礼服"), item(201518, "头环"), item(204505, "护臂"), item(205501, "战靴"),
            item(112402, "强弓"), item(202514, "风衣"), item(201503, "面具"), item(204418, "箭筒"), item(203510, "轻靴"),
            item(112105, "反曲弓"), item(202503, "战装"), item(201410, "冠冕"), item(204410, "护腕"), item(203506, "长靴"),
        )
        val skillOrders = listOf(
            listOf("Q", "W", "E", "Q", "Q", "R", "Q", "W", "Q", "W", "R", "W", "W", "E", "E"),
            listOf("Q", "E", "W", "Q", "Q", "R", "Q", "E", "Q", "E", "R", "E", "E", "W", "W"),
            listOf("W", "Q", "E", "Q", "Q", "R", "Q", "W", "Q", "W", "R", "W", "E", "W", "E"),
            listOf("E", "Q", "W", "Q", "Q", "R", "Q", "E", "Q", "E", "R", "W", "E", "W", "W"),
        )
        val skillBuilds = skillOrders.mapIndexed { index, order ->
            CharacterDetail.SkillBuild(
                priority = listOf("T", if (index % 2 == 0) "Q" else "E", "W", if (index % 2 == 0) "E" else "Q"),
                order = order,
                pickRate = listOf(31.8, 24.6, 17.3, 10.9)[index],
                winRate = listOf(53.7, 52.9, 51.8, 50.6)[index],
            )
        }
        val tacticals = listOf(
            tactical(500170, "强力冲击", 37.2, 54.1),
            tactical(30, "闪灵", 26.4, 52.8),
            tactical(190, "超越", 19.7, 51.9),
            tactical(500170, "精准射击", 9.8, 50.7),
        )
        val traitIds = listOf(7000201L, 7000501L, 7000601L, 7010501L, 7010701L, 7011001L, 7011101L, 7011201L, 7111101L, 7200201L, 7300101L, 7300201L)
        val augments = (0 until 4).map { index ->
            val coreId = traitIds[index]
            CharacterDetail.Augment(
                core = trait(coreId, listOf("吸血", "霹雳", "狂热", "制动力")[index], 29.5 - index * 4.1),
                subs = (0 until 6).map { offset ->
                    val id = traitIds[(index * 2 + offset + 4) % traitIds.size]
                    trait(id, "副潜能${offset + 1}", 0.0)
                },
            )
        }
        val itemBuilds = (0 until 4).map { index ->
            val set = items.subList(index * 5, index * 5 + 5)
            CharacterDetail.ItemBuild(
                items = set,
                order = set,
                pickRate = listOf(28.4, 21.7, 15.2, 9.6)[index],
                winRate = listOf(55.2, 53.8, 52.4, 51.1)[index],
            )
        }
        val equipments = listOf("武器", "胸甲", "头部", "手臂", "腿部").mapIndexed { index, slot ->
            val item = items[index]
            CharacterDetail.EquipmentSlotPick(slot, item.id, item.name, item.iconUrl, item.bgUrl, listOf(36.1, 31.8, 29.4, 27.6, 33.2)[index])
        }
        val weapon = CharacterDetail.WeaponBuild(
            weaponId = 5,
            weapon = "弓",
            iconUrl = asset("resources/images/weapon/5.png"),
            tier = "S",
            tierScore = 92.6,
            games = 18420,
            pickRate = 8.7,
            winRate = 52.8,
            top3Rate = 41.6,
            avgRank = 4.2,
            avgKills = 3.8,
            rank = 3,
            rankSize = 72,
            skills = skills,
            skillBySlot = skills.associateBy { it.slot },
            skillBuilds = skillBuilds,
            topEquipments = equipments,
            itemBuilds = itemBuilds,
            tacticals = tacticals,
            augments = augments,
            infusions = emptyList(),
        )
        return CharacterDetail(
            id = 24,
            name = "莉央",
            title = "穿越风林火山的巫女",
            imageUrl = asset("resources/images/character/24/CharResult/1024000.png"),
            archetypes = listOf("远程", "持续输出", "射手"),
            analysis = CharacterDetail.CharacterAnalysis(
                tier = "diamond_plus",
                tierLabel = "灭钻",
                matchingModeLabel = "排位",
                teamModeLabel = "三人",
                updatedLabel = "2小时前",
                patchLabel = "8.4",
                totalGames = 211_650,
                characterGames = 18_420,
                pickRate = 8.7,
                characterTier = "S",
                weapons = listOf(weapon),
                topPlayers = listOf(
                    player("RioMaster", 8120, 8), player("BowOnly", 7985, 8), player("风林火山", 7812, 7),
                    player("巫女修行", 7650, 7), player("弓道部", 7528, 7), player("箭雨", 7390, 7),
                ),
            ),
            httpServer = "",
        )
    }

    private fun skill(id: Long, slot: String, name: String) = CharacterDetail.SkillSlot(id, slot, name, asset("resources/images/skill/$id.png"))
    private fun item(id: Long, name: String) = CharacterDetail.Pick(id, name, asset("resources/images/item/$id.png"), asset("resources/images/item/bg/5.svg"), 0.0, 0.0)
    private fun tactical(id: Long, name: String, pick: Double, win: Double) = CharacterDetail.Pick(id, name, asset("resources/images/tactical/skill/$id.png"), "", pick, win)
    private fun trait(id: Long, name: String, pick: Double) = CharacterDetail.Pick(id, name, asset("resources/images/trait/skill/$id.png"), "", pick, 52.0)
    private fun player(name: String, mmr: Long, tier: Int) = CharacterDetail.TopPlayer(name, mmr, "Eternal", asset("resources/images/tier/round/$tier.png"))
    private fun asset(relative: String): String = root.resolve(relative).toUri().toString()
}
