package cn.luorenmu.plugins.character

import cn.luorenmu.request.entity.module.DakGGRank
import kotlinx.coroutines.runBlocking
import love.forte.simbot.message.toText
import kotlin.test.*

class CharacterWeaponSelectionTest {
    @Test fun `rank can directly follow character or follow mode or weapon`() {
        for (tokens in listOf(listOf("无暇"), listOf("排位", "无暇"), listOf("弓", "无暇"), listOf("无暇", "弓", "排位"))) {
            val options = CharacterDetailOptions.parse(listOf("mode", "tier", "weapon").zip(tokens).toMap())
            assertEquals("mithril_plus", options.tierKey)
            assertEquals(if ("弓" in tokens) "弓" else null, options.weapon)
        }
        assertEquals(DakGGRank.IN_1000, CharacterDetailOptions.parse(mapOf("mode" to "in1000")).rank)
        assertEquals(DakGGRank.DIAMOND_PLUS, CharacterDetailOptions.parse(emptyMap()).rank)
        assertNull(CharacterDetailOptions.parse(mapOf("mode" to "钴协议")).tierKey)
    }

    @Test fun `callback only accepts character mode rank and weapon fields`() {
        assertEquals("/查询角色 1 钴协议 2", CharacterWeaponCallback.decode("lomu:character:v1:1:COBALT:none:2")?.command)
        for (payload in listOf("/帮助", "lomu:character:v1:1:RANK:invalid:2", "lomu:character:v1:1:COBALT:gold:2",
            "lomu:character:v1:-1:RANK:gold:2", "lomu:character:v1:1:RANK:gold:2:/帮助")) {
            assertNull(CharacterWeaponCallback.decode(payload))
        }
    }

    @Test fun `click is acknowledged before query and duplicate event does not send twice`() = runBlocking {
        val order = mutableListOf<String>()
        val handler = CharacterWeaponInteractionHandler(query = {
            order += "query"
            assertEquals("/查询角色 1 排位 无暇 2", it.plainText)
            "image-result".toText()
        })
        val event = CharacterWeaponInteraction("interaction", "outer-event", 11, 1,
            "a".repeat(32), "b".repeat(32), "character-weapon-1-2", "lomu:character:v1:1:RANK:mithril_plus:2")
        repeat(2) {
            assertTrue(handler.handle(event, { code -> assertEquals(0, code); order += "ack" }, { group, id, _ ->
                assertEquals(event.groupId, group)
                assertEquals("outer-event", id)
                order += "send"
            }))
        }
        assertEquals(listOf("ack", "query", "send"), order)
    }

    @Test fun `malformed callback is rejected and other plugins are ignored`() = runBlocking {
        val handler = CharacterWeaponInteractionHandler(query = { error("must not run") })
        val event = CharacterWeaponInteraction("invalid", "outer", 11, 1, "a".repeat(32), "b".repeat(32),
            "character-weapon-1-2", "lomu:character:v1:1:RANK:invalid:2")
        assertTrue(handler.handle(event, { assertEquals(1, it) }, { _, _, _ -> error("must not send") }))
        assertFalse(handler.handle(event.copy(buttonId = "test-callback"), { error("must not ack") }, { _, _, _ -> error("must not send") }))
    }
}
