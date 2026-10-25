package cn.luorenmu.service

import cn.luorenmu.qq.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class QQMenuPanelStatus(val available: Boolean, val message: String)

@Serializable
data class QQMenuUpdateRequest(val menu: QQCustomMenu?, val confirmed: Boolean = false)
@Serializable
data class QQPanelCreateRequest(val configuration: QQPanelCreate, val confirmed: Boolean = false)
@Serializable
data class QQPanelUpdateRequest(val panel: QQCommandPanel, val confirmed: Boolean = false)
@Serializable
data class QQPanelTargetsRequest(val targets: QQPanelTargets, val confirmed: Boolean = false)
@Serializable
data class QQDeletePanelRequest(val confirmed: Boolean = false)

class AdminQQMenuPanelService(private val client: QQMenuPanelClient? = null) {
    private val writes = Mutex()
    fun status(): QQMenuPanelStatus = when {
        client == null -> QQMenuPanelStatus(false, "仅 QQ 官方机器人适配器支持菜单与指令面板。")
        !client.available -> QQMenuPanelStatus(false, "QQ 机器人未连接，请检查凭据与机器人状态。")
        else -> QQMenuPanelStatus(true, "已连接 QQ；实际可用能力由 QQ 接口权限决定。变更只在明确提交时发送，不在启动时自动覆盖。")
    }
    private fun connected(): QQMenuPanelClient {
        val state = status()
        if (!state.available) throw QQMenuPanelUnavailable(state.message)
        return checkNotNull(client)
    }
    private suspend fun <T> read(block: suspend (QQMenuPanelClient) -> T): T = withTimeout(30_000) { block(connected()) }
    private suspend fun <T> write(confirmed: Boolean, block: suspend (QQMenuPanelClient) -> T): T {
        require(confirmed) { "请确认此操作会修改 QQ 线上配置。" }
        return writes.withLock { read(block) }
    }

    suspend fun menu() = read { it.getMenu() }
    suspend fun putMenu(request: QQMenuUpdateRequest): QQVersion {
        request.menu?.let(::validateMenu)
        return write(request.confirmed) { it.putMenu(request.menu) }
    }
    suspend fun panels(scope: String, cursor: String?, limit: Int): QQPanelPage {
        validateScope(scope)
        require(limit in 1..50) { "每页数量必须为 1–50。" }
        require(cursor == null || cursor.length <= 2048) { "分页游标过长。" }
        return read { it.listPanels(scope, cursor?.takeIf(String::isNotBlank), limit) }
    }
    suspend fun panel(id: String): QQPanelRecord {
        validateId(id)
        return read { it.getPanel(id) }
    }
    suspend fun create(request: QQPanelCreateRequest): QQPanelCreated {
        val value = request.configuration
        validateScope(value.scope)
        require(value.targetType in setOf("all", "specific")) { "请选择全部生效或指定对象。" }
        require(value.scope in setOf("c2c", "group") || value.targetType == "all") { "频道和频道私信只支持全部生效。" }
        validatePanel(value.panel)
        if (value.targetType == "all") {
            require(value.userOpenids.isNullOrEmpty() && value.groupOpenids.isNullOrEmpty()) { "全部生效时不能指定 OpenID。" }
        } else validateTargets(value.scope, value.userOpenids, value.groupOpenids)
        return write(request.confirmed) { it.createPanel(value.copy(panel = value.panel.copy(version = null))) }
    }
    suspend fun putPanel(id: String, request: QQPanelUpdateRequest): QQVersion {
        validateId(id); validatePanel(request.panel)
        return write(request.confirmed) { it.putPanel(id, request.panel.copy(version = null)) }
    }
    suspend fun targets(id: String, request: QQPanelTargetsRequest): Boolean {
        validateId(id)
        require(request.targets.op in setOf("add", "del")) { "关联操作必须为 add 或 del。" }
        return write(request.confirmed) { client ->
            val existing = client.getPanel(id)
            require(existing.targetType == "specific") { "只有指定对象的面板可以管理关联对象。" }
            validateTargets(existing.scope, request.targets.userOpenids, request.targets.groupOpenids)
            client.putTargets(id, request.targets)
            true
        }
    }
    suspend fun delete(id: String, request: QQDeletePanelRequest): Boolean {
        validateId(id)
        return write(request.confirmed) { it.deletePanel(id); true }
    }

    private fun validateScope(scope: String) = require(scope in setOf("group", "c2c", "channel", "dm")) { "无效的面板生效场景。" }
    private fun validateId(id: String) = require(id.matches(Regex("[A-Za-z0-9_-]{1,256}"))) { "无效面板 ID。" }
    private fun validateTargets(scope: String, users: List<String>?, groups: List<String>?) {
        require(scope in setOf("group", "c2c")) { "该场景不支持指定关联对象。" }
        val ids = if (scope == "group") groups else users
        val wrong = if (scope == "group") users else groups
        require(wrong.isNullOrEmpty()) { "群聊只能传群 OpenID，单聊只能传用户 OpenID。" }
        require(ids != null && ids.size in 1..20 && ids.distinct().size == ids.size &&
            ids.all { it.matches(Regex("[A-Fa-f0-9]{32}")) }) { "请提供 1–20 个不重复的 32 位十六进制 OpenID。" }
    }
    private fun validatePanel(panel: QQCommandPanel) {
        require(panel.items.size in 1..20) { "指令面板必须包含 1–20 个元素。" }
        require((panel.remark?.length ?: 0) <= 255) { "面板备注不能超过 255 个字符。" }
        panel.items.forEach {
            require(it.name.isNotBlank() && it.name.length <= 14) { "面板元素名称不能为空且不能超过 14 个字符（QQ 显示宽度约 7 个汉字）。" }
            require((it.desc?.length ?: 0) <= 30) { "面板元素描述不能超过 30 个字符。" }
            require(it.type in setOf("command", "link")) { "面板元素类型必须为 command 或 link。" }
            if (it.type == "link") validateLink(it.link)
            else require(it.link == null) { "指令类型不能同时携带链接。" }
        }
    }
    private fun validateMenu(menu: QQCustomMenu) {
        // These are application safety bounds, not invented QQ platform limits; QQ remains authoritative.
        require(menu.items.size <= 20) { "本管理页单次最多提交 20 个一级菜单。" }
        menu.items.forEach { item ->
            validateMenuName(item.name)
            require(item.type in setOf("menu", "send_message", "link", "switch")) { "无效的菜单类型。" }
            when (item.type) {
                "menu" -> {
                    require(item.subMenuItems != null && item.subMenuItems.size in 1..20) { "二级菜单必须包含 1–20 个菜单项。" }
                    item.subMenuItems.forEach {
                        validateMenuName(it.name)
                        require(it.type in setOf("send_message", "link")) { "二级菜单只支持填入消息或链接。" }
                        if (it.type == "link") { validateLink(it.link); require(it.sendMessage == null) { "链接菜单不能同时填入消息。" } }
                        else { validateMessage(it.sendMessage); require(it.link == null) { "消息菜单不能同时指定链接。" } }
                    }
                    require(item.sendMessage == null && item.link == null && item.switch == null) { "父菜单不能同时配置动作。" }
                }
                "send_message" -> { validateMessage(item.sendMessage); require(item.link == null && item.switch == null && item.subMenuItems == null) { "消息菜单包含无效动作。" } }
                "link" -> { validateLink(item.link); require(item.sendMessage == null && item.switch == null && item.subMenuItems == null) { "链接菜单包含无效动作。" } }
                "switch" -> {
                    require(item.switch?.switchId?.matches(Regex("[A-Za-z0-9_-]{1,128}")) == true) { "开关标识不能为空，只允许字母、数字、下划线和短横线。" }
                    require(item.sendMessage == null && item.link == null && item.subMenuItems == null) { "开关菜单包含无效动作。" }
                }
            }
        }
    }
    private fun validateMenuName(name: String) = require(name.isNotBlank() && name.length <= 128) { "菜单名称不能为空且不能超过 128 个字符，QQ 可能有更严格限制。" }
    private fun validateMessage(text: String?) = require(!text.isNullOrBlank() && text.length <= 2048) { "待填入消息不能为空或超过 2048 个字符。" }
    private fun validateLink(value: String?) {
        val uri = runCatching { URI(value.orEmpty()) }.getOrNull()
        require(value != null && value.length <= 2048 && uri != null && uri.scheme in setOf("https", "http") &&
            !uri.host.isNullOrBlank() && uri.userInfo == null) { "请输入有效的 HTTP／HTTPS 链接，不支持脚本、文件或带认证信息的地址。" }
    }
}
