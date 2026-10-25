package cn.luorenmu.qq

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Host-owned wire models for QQ menu/panel APIs; see upstream simbot-component-qq-guild PR #391. */
@Serializable
data class QQCustomMenu(val items: List<QQMenuItem> = emptyList())

@Serializable
data class QQMenuItem(
    val name: String,
    val type: String,
    @SerialName("send_message") val sendMessage: String? = null,
    val link: String? = null,
    val switch: QQMenuSwitch? = null,
    @SerialName("sub_menu_items") val subMenuItems: List<QQSubMenuItem>? = null,
)

@Serializable
data class QQSubMenuItem(val name: String, val type: String,
    @SerialName("send_message") val sendMessage: String? = null, val link: String? = null)

@Serializable
data class QQMenuSwitch(@SerialName("switch_id") val switchId: String,
    @SerialName("default") val defaultValue: Boolean = false)

@Serializable
data class QQMenuSnapshot(val version: Int = 0, val menu: QQCustomMenu? = null)

@Serializable
data class QQVersion(val version: Int = 0)

@Serializable
data class QQPanelItem(val name: String, val desc: String? = null, val type: String = "command",
    @SerialName("only_admin") val onlyAdmin: Boolean = false, val link: String? = null)

@Serializable
data class QQCommandPanel(val items: List<QQPanelItem>, val remark: String? = null, val version: Int? = null)

@Serializable
data class QQPanelRecord(
    @SerialName("panel_id") val panelId: String,
    val scope: String,
    @SerialName("target_type") val targetType: String,
    val panel: QQCommandPanel,
    @SerialName("user_openids") val userOpenids: List<String>? = null,
    @SerialName("group_openids") val groupOpenids: List<String>? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val version: Int? = null,
)

@Serializable
data class QQPanelPage(val records: List<QQPanelRecord> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String = "", @SerialName("is_end") val isEnd: Boolean = false)

@Serializable
data class QQPanelCreate(val scope: String, @SerialName("target_type") val targetType: String,
    val panel: QQCommandPanel,
    @SerialName("user_openids") val userOpenids: List<String>? = null,
    @SerialName("group_openids") val groupOpenids: List<String>? = null)

@Serializable
data class QQPanelCreated(@SerialName("panel_id") val panelId: String)

@Serializable
data class QQPanelTargets(val op: String,
    @SerialName("user_openids") val userOpenids: List<String>? = null,
    @SerialName("group_openids") val groupOpenids: List<String>? = null)

/** Implemented by the QQ adapter. OneBot and an unconnected QQ adapter never pretend this is usable. */
interface QQMenuPanelClient {
    val available: Boolean
    suspend fun getMenu(): QQMenuSnapshot
    suspend fun putMenu(menu: QQCustomMenu?): QQVersion
    suspend fun listPanels(scope: String, cursor: String?, limit: Int): QQPanelPage
    suspend fun getPanel(id: String): QQPanelRecord
    suspend fun createPanel(configuration: QQPanelCreate): QQPanelCreated
    suspend fun putPanel(id: String, panel: QQCommandPanel): QQVersion
    suspend fun putTargets(id: String, targets: QQPanelTargets)
    suspend fun deletePanel(id: String)
}

class QQMenuPanelUnavailable(message: String) : IllegalStateException(message)
class QQMenuPanelRemoteFailure(val code: Int?) : RuntimeException(
    if (code == 11253) "QQ 未授予菜单／指令面板接口权限（11253），请在 QQ 开放平台检查接口权限。"
    else "QQ 接口请求失败${code?.let { "（错误码 $it）" }.orEmpty()}，请检查接口权限及参数。修改结果可能已生效，请先重新读取列表，避免重复创建。")
