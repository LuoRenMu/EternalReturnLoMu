package cn.luorenmu.api

import cn.luorenmu.qq.QQMenuPanelRemoteFailure
import cn.luorenmu.qq.QQMenuPanelUnavailable
import cn.luorenmu.service.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.header
import io.ktor.server.routing.*
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.koin.java.KoinJavaComponent.inject

private val qqAdminJson = Json { ignoreUnknownKeys = false }
private val qqAdminLog = KotlinLogging.logger {}

internal fun Route.adminQQMenuPanelRouting(serviceProvider: (() -> AdminQQMenuPanelService)? = null) {
    val injected: AdminQQMenuPanelService by inject(AdminQQMenuPanelService::class.java)
    fun service() = serviceProvider?.invoke() ?: injected
    route("/api/admin/qq") {
        get("/status") { call.qqAdminAction { service().status() } }
        get("/menu") { call.qqAdminAction { service().menu() } }
        put("/menu") { call.qqAdminAction { service().putMenu(call.qqBody()) } }
        get("/panels") {
            call.qqAdminAction {
                val limit = call.request.queryParameters["limit"]?.let {
                    it.toIntOrNull() ?: throw IllegalArgumentException("每页数量必须为整数。")
                } ?: 20
                service().panels(call.request.queryParameters["scope"] ?: "group", call.request.queryParameters["cursor"], limit)
            }
        }
        post("/panels") { call.qqAdminAction { service().create(call.qqBody()) } }
        get("/panels/{id}") { call.qqAdminAction { service().panel(call.parameters["id"].orEmpty()) } }
        put("/panels/{id}") { call.qqAdminAction { service().putPanel(call.parameters["id"].orEmpty(), call.qqBody()) } }
        put("/panels/{id}/target") { call.qqAdminAction { service().targets(call.parameters["id"].orEmpty(), call.qqBody()) } }
        delete("/panels/{id}") { call.qqAdminAction { service().delete(call.parameters["id"].orEmpty(), call.qqBody()) } }
    }
}

private suspend inline fun <reified T> ApplicationCall.qqBody(): T {
    require(request.header(HttpHeaders.ContentType)?.substringBefore(';')?.trim()?.lowercase() == "application/json") {
        "请使用 application/json 提交配置。"
    }
    val bytes = receiveChannel().readRemaining(65_537).readBytes()
    require(bytes.size <= 65_536) { "配置内容不能超过 64 KiB。" }
    return qqAdminJson.decodeFromString(bytes.toString(Charsets.UTF_8))
}

private suspend inline fun <reified T> ApplicationCall.qqAdminAction(block: () -> T) {
    if (!requireAdmin()) return
    try {
        respondJson(AdminResponse(data = block()))
    } catch (error: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        respondJson(AdminResponse<Unit>(ok = false, error = "QQ 接口响应超时。修改结果可能已生效，请先重新读取，不要盲目重复创建。"), HttpStatusCode.GatewayTimeout)
    } catch (error: CancellationException) { throw error
    } catch (error: QQMenuPanelUnavailable) {
        respondJson(AdminResponse<Unit>(ok = false, error = error.message), HttpStatusCode.ServiceUnavailable)
    } catch (error: QQMenuPanelRemoteFailure) {
        respondJson(AdminResponse<Unit>(ok = false, error = error.message), HttpStatusCode.BadGateway)
    } catch (_: SerializationException) {
        respondJson(AdminResponse<Unit>(ok = false, error = "配置 JSON 格式或字段不正确，请检查后重试。"), HttpStatusCode.BadRequest)
    } catch (error: IllegalArgumentException) {
        respondJson(AdminResponse<Unit>(ok = false, error = error.message ?: "参数无效。"), HttpStatusCode.BadRequest)
    } catch (error: Exception) {
        qqAdminLog.warn { "QQ 菜单／面板操作失败：${error.javaClass.simpleName}" }
        respondJson(AdminResponse<Unit>(ok = false, error = "QQ 配置操作失败，请稍后重新读取状态。"), HttpStatusCode.InternalServerError)
    }
}
