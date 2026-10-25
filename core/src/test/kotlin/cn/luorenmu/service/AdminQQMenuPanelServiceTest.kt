package cn.luorenmu.service

import cn.luorenmu.qq.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AdminQQMenuPanelServiceTest {
    private val group = "A".repeat(32)
    private val user = "B".repeat(32)
    private fun panel() = QQCommandPanel(listOf(QQPanelItem("/帮助", "查询命令")), "test", version = 99)

    @Test fun `panel text limits count non ASCII code points twice before invoking QQ`() = runBlocking {
        val client = FakeQQMenuPanelClient()
        val service = AdminQQMenuPanelService(client)
        val invalid = listOf(
            QQPanelItem("查".repeat(8)), QQPanelItem("/" + "查".repeat(7)),
            QQPanelItem("help", "查".repeat(16)), QQPanelItem("한".repeat(8)),
            QQPanelItem("😀".repeat(7) + "a"),
        )
        for (item in invalid) {
            val value = QQCommandPanel(listOf(item))
            assertFailsWith<IllegalArgumentException> { service.create(QQPanelCreateRequest(QQPanelCreate("group", "all", value), true)) }
            assertFailsWith<IllegalArgumentException> { service.putPanel("p1", QQPanelUpdateRequest(value, true)) }
        }
        assertEquals(0, client.writes)
        for (item in listOf(QQPanelItem("查".repeat(7), "查".repeat(15)), QQPanelItem("/" + "查".repeat(6) + "a"),
            QQPanelItem("a".repeat(14), "a".repeat(30)), QQPanelItem("😀".repeat(7)))) {
            service.create(QQPanelCreateRequest(QQPanelCreate("group", "all", QQCommandPanel(listOf(item))), true))
        }
        assertEquals(4, client.writes)
    }

    @Test fun `menu item counts and names enforce QQ first and second level limits`() = runBlocking {
        val client = FakeQQMenuPanelClient()
        val service = AdminQQMenuPanelService(client)
        val item = QQMenuItem("帮助", "send_message", sendMessage = "/帮助")
        val child = QQSubMenuItem("查询", "send_message", "/帮助")
        val invalid = listOf(
            QQCustomMenu(List(11) { item }), QQCustomMenu(listOf(item.copy(name = "查".repeat(6)))),
            QQCustomMenu(listOf(QQMenuItem("更多", "menu", subMenuItems = List(6) { child }))),
            QQCustomMenu(listOf(QQMenuItem("更多", "menu", subMenuItems = listOf(child.copy(name = "查".repeat(8)))))),
        )
        for (menu in invalid) assertFailsWith<IllegalArgumentException> { service.putMenu(QQMenuUpdateRequest(menu, true)) }
        assertEquals(0, client.writes)
        service.putMenu(QQMenuUpdateRequest(QQCustomMenu(List(10) { item.copy(name = "查".repeat(5)) }), true))
        service.putMenu(QQMenuUpdateRequest(QQCustomMenu(listOf(QQMenuItem("更多", "menu",
            subMenuItems = List(5) { child.copy(name = "查".repeat(7)) }))), true))
        assertEquals(2, client.writes)
    }

    @Test fun `reads never publish and all menu actions round trip including explicit clear`() = runBlocking {
        val client = FakeQQMenuPanelClient()
        val service = AdminQQMenuPanelService(client)
        assertTrue(service.status().available)
        service.menu(); service.panels("group", "next+/=", 50)
        assertEquals(0, client.writes)
        assertEquals(Triple("group", "next+/=", 50), client.pageQuery)
        val menu = QQCustomMenu(listOf(
            QQMenuItem("命令", "send_message", sendMessage = "/帮助"),
            QQMenuItem("链接", "link", link = "https://example.com/a"),
            QQMenuItem("开关", "switch", switch = QQMenuSwitch("notice", true)),
            QQMenuItem("更多", "menu", subMenuItems = listOf(QQSubMenuItem("查询", "send_message", "/群宠"))),
        ))
        service.putMenu(QQMenuUpdateRequest(menu, true))
        assertEquals(menu, service.menu().menu)
        service.putMenu(QQMenuUpdateRequest(null, true))
        assertNull(service.menu().menu)
        assertEquals(2, client.writes)
    }

    @Test fun `panel CRUD separates content from targets and supports pagination scopes`() = runBlocking {
        val client = FakeQQMenuPanelClient()
        val service = AdminQQMenuPanelService(client)
        for (scope in listOf("group", "c2c", "channel", "dm")) {
            service.panels(scope, null, 20)
            val result = service.create(QQPanelCreateRequest(QQPanelCreate(scope, "all", panel()), true))
            assertNull(client.records.getValue(result.panelId).panel.version)
        }
        val created = service.create(QQPanelCreateRequest(QQPanelCreate("group", "specific", panel(), groupOpenids = listOf(group)), true))
        service.putPanel(created.panelId, QQPanelUpdateRequest(panel().copy(remark = "updated"), true))
        assertEquals(listOf(group), service.panel(created.panelId).groupOpenids)
        assertNull(service.panel(created.panelId).panel.version)
        service.targets(created.panelId, QQPanelTargetsRequest(QQPanelTargets("add", groupOpenids = listOf(user)), true))
        assertEquals(QQPanelTargets("add", groupOpenids = listOf(user)), client.lastTargets)
        assertTrue(service.delete(created.panelId, QQDeletePanelRequest(true)))
        assertFalse(client.records.containsKey(created.panelId))
    }

    @Test fun `all mutations need explicit confirmation and unavailable adapters never invoke a client`() = runBlocking {
        val client = FakeQQMenuPanelClient()
        val service = AdminQQMenuPanelService(client)
        assertFailsWith<IllegalArgumentException> { service.putMenu(QQMenuUpdateRequest(null)) }
        assertFailsWith<IllegalArgumentException> { service.create(QQPanelCreateRequest(QQPanelCreate("group", "all", panel()))) }
        assertFailsWith<IllegalArgumentException> { service.putPanel("p1", QQPanelUpdateRequest(panel())) }
        assertFailsWith<IllegalArgumentException> { service.targets("p1", QQPanelTargetsRequest(QQPanelTargets("add", groupOpenids = listOf(group)))) }
        assertFailsWith<IllegalArgumentException> { service.delete("p1", QQDeletePanelRequest()) }
        assertEquals(0, client.writes)
        assertFalse(AdminQQMenuPanelService().status().available)
        assertFailsWith<QQMenuPanelUnavailable> { AdminQQMenuPanelService().menu() }
        client.available = false
        assertFailsWith<QQMenuPanelUnavailable> { service.panels("group", null, 20) }
        assertEquals(0, client.writes)
    }

    @Test fun `invalid items scopes links and target combinations fail before any mutation`() = runBlocking {
        val client = FakeQQMenuPanelClient()
        val service = AdminQQMenuPanelService(client)
        val invalidPanels = listOf(panel().copy(items = emptyList()), panel().copy(items = List(21) { QQPanelItem("x") }),
            panel().copy(remark = "x".repeat(256)), QQCommandPanel(listOf(QQPanelItem("x".repeat(15)))),
            QQCommandPanel(listOf(QQPanelItem("x", desc = "x".repeat(31)))),
            QQCommandPanel(listOf(QQPanelItem("x", type = "invalid"))), QQCommandPanel(listOf(QQPanelItem("x", type = "link", link = "javascript:alert(1)"))))
        for (p in invalidPanels) assertFailsWith<IllegalArgumentException> { service.create(QQPanelCreateRequest(QQPanelCreate("group", "all", p), true)) }
        for (scope in listOf("bad", "GROUP")) assertFailsWith<IllegalArgumentException> { service.panels(scope, null, 20) }
        for (limit in listOf(0, 51)) assertFailsWith<IllegalArgumentException> { service.panels("group", null, limit) }
        val invalidCreates = listOf(
            QQPanelCreate("channel", "specific", panel(), groupOpenids = listOf(group)),
            QQPanelCreate("group", "specific", panel()),
            QQPanelCreate("group", "specific", panel(), userOpenids = listOf(user)),
            QQPanelCreate("c2c", "specific", panel(), groupOpenids = listOf(group)),
            QQPanelCreate("group", "all", panel(), groupOpenids = listOf(group)),
            QQPanelCreate("group", "specific", panel(), groupOpenids = List(21) { group }),
            QQPanelCreate("group", "specific", panel(), groupOpenids = listOf("12345")),
        )
        for (p in invalidCreates) assertFailsWith<IllegalArgumentException> { service.create(QQPanelCreateRequest(p, true)) }
        for (id in listOf("../menu", "a/b", "a%2fb", "")) assertFailsWith<IllegalArgumentException> { service.delete(id, QQDeletePanelRequest(true)) }
        val menus = listOf(QQMenuItem("", "send_message", "/help"), QQMenuItem("bad", "send_message"),
            QQMenuItem("bad", "link", link = "file:///etc/passwd"), QQMenuItem("bad", "switch"),
            QQMenuItem("bad", "menu", subMenuItems = emptyList()), QQMenuItem("bad", "menu", subMenuItems = listOf(QQSubMenuItem("x", "switch"))))
        for (m in menus) assertFailsWith<IllegalArgumentException> { service.putMenu(QQMenuUpdateRequest(QQCustomMenu(listOf(m)), true)) }
        assertEquals(0, client.writes)
    }

    @Test fun `target update uses authoritative stored scope and cannot edit a global panel`() = runBlocking {
        val client = FakeQQMenuPanelClient()
        val service = AdminQQMenuPanelService(client)
        client.records["p1"] = QQPanelRecord("p1", "c2c", "specific", panel())
        assertFailsWith<IllegalArgumentException> { service.targets("p1", QQPanelTargetsRequest(QQPanelTargets("add", groupOpenids = listOf(group)), true)) }
        service.targets("p1", QQPanelTargetsRequest(QQPanelTargets("del", userOpenids = listOf(user)), true))
        client.records["p1"] = client.records.getValue("p1").copy(targetType = "all")
        assertFailsWith<IllegalArgumentException> { service.targets("p1", QQPanelTargetsRequest(QQPanelTargets("add", userOpenids = listOf(user)), true)) }
        assertEquals(1, client.writes)
    }
}

internal class FakeQQMenuPanelClient : QQMenuPanelClient {
    override var available = true
    var writes = 0
    var currentMenu: QQCustomMenu? = null
    val records = linkedMapOf<String, QQPanelRecord>()
    var pageQuery: Triple<String, String?, Int>? = null
    var lastTargets: QQPanelTargets? = null
    var failure: RuntimeException? = null
    override suspend fun getMenu(): QQMenuSnapshot { failure?.let { throw it }; return QQMenuSnapshot(writes, currentMenu) }
    override suspend fun putMenu(menu: QQCustomMenu?): QQVersion { failure?.let { throw it }; currentMenu = menu; return QQVersion(++writes) }
    override suspend fun listPanels(scope: String, cursor: String?, limit: Int): QQPanelPage {
        pageQuery = Triple(scope, cursor, limit)
        return QQPanelPage(records.values.filter { it.scope == scope }, "", true)
    }
    override suspend fun getPanel(id: String) = records.getValue(id)
    override suspend fun createPanel(configuration: QQPanelCreate): QQPanelCreated {
        val id = "p${++writes}"
        records[id] = QQPanelRecord(id, configuration.scope, configuration.targetType, configuration.panel,
            configuration.userOpenids, configuration.groupOpenids)
        return QQPanelCreated(id)
    }
    override suspend fun putPanel(id: String, panel: QQCommandPanel): QQVersion {
        records[id] = records.getValue(id).copy(panel = panel)
        return QQVersion(++writes)
    }
    override suspend fun putTargets(id: String, targets: QQPanelTargets) { lastTargets = targets; writes++ }
    override suspend fun deletePanel(id: String) { records.remove(id); writes++ }
}
