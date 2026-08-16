package cn.luorenmu.api

import cn.luorenmu.command.CommandRouter
import cn.luorenmu.command.plugin.CommandPlugins
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.repository.StatisticsRepository
import cn.luorenmu.repository.ExceptionRepository
import cn.luorenmu.service.AdminConfigService
import cn.luorenmu.service.AdminDatabaseService
import cn.luorenmu.service.AdminSystemService
import cn.luorenmu.service.AdminSystemView
import cn.luorenmu.service.AdminRowUpdate
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.freemarker.FreeMarkerContent
import io.ktor.server.request.receiveText
import io.ktor.server.request.receiveMultipart
import io.ktor.http.content.*
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import love.forte.simbot.common.id.ID
import love.forte.simbot.common.id.StringID
import love.forte.simbot.message.Message
import love.forte.simbot.message.Messages
import love.forte.simbot.message.OfflineFileImage
import love.forte.simbot.message.OfflinePathImage
import love.forte.simbot.message.OfflineResourceImage
import love.forte.simbot.message.OfflineURIImage
import love.forte.simbot.message.PlainText
import org.koin.java.KoinJavaComponent.inject
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 *
 * @author LoMu
 * Date 2026/8/16 15:30
 */
private val adminJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Route.adminRouting() {
    val statisticsRepository: StatisticsRepository by inject(StatisticsRepository::class.java)
    val exceptionRepository: ExceptionRepository by inject(ExceptionRepository::class.java)
    val configService: AdminConfigService by inject(AdminConfigService::class.java)
    val databaseService: AdminDatabaseService by inject(AdminDatabaseService::class.java)
    val systemService: AdminSystemService by inject(AdminSystemService::class.java)
    val commandRouter = CommandRouter()

    get("/") { call.respondAdminDashboard() }
    get("/admin") { call.respondAdminDashboard() }
    post("/admin/login") {
        val supplied = call.receiveParameters()[AdminAccessToken.QUERY_NAME].orEmpty()
        if (!AdminAccessToken.matches(supplied)) {
            call.respondAdminLogin("访问令牌无效，请检查后重试。", HttpStatusCode.Unauthorized)
            return@post
        }
        call.startAdminSession(supplied.trim())
        call.respondRedirect("/admin")
    }

    get("/admin/fragments/system") {
        if (!call.hasValidAdminToken()) {
            call.respond(
                HttpStatusCode.Unauthorized,
                FreeMarkerContent("admin/fragments/system.ftl", mapOf("error" to "管理令牌无效")),
            )
            return@get
        }
        call.respond(
            FreeMarkerContent(
                "admin/fragments/system.ftl",
                mapOf("runtime" to systemService.view().toFragmentView()),
            )
        )
    }

    get("/api/admin/command-usages") {
        if (!call.requireAdmin()) return@get
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 100
        call.respondJson(AdminResponse(data = statisticsRepository.listCommandUsages(limit)))
    }

    put("/api/admin/command-usages/{id}") {
        if (!call.requireAdmin()) return@put
        val id = call.parameters["id"]?.toLongOrNull()
        if (id == null) {
            call.respondJson(AdminResponse<Unit>(ok = false, error = "无效记录 ID"), HttpStatusCode.BadRequest)
            return@put
        }

        val request = adminJson.decodeFromString<UpdateCommandUsageRequest>(call.receiveText())
        val updated = statisticsRepository.updateCommandUsage(
            id = id,
            commandName = request.commandName.trim(),
            nickname = request.nickname.blankToNull(),
            groupId = request.groupId.blankToNull(),
            senderId = request.senderId.blankToNull(),
        )

        if (updated) {
            call.respondJson(AdminResponse(data = true))
        } else {
            call.respondJson(AdminResponse<Unit>(ok = false, error = "记录不存在或未更新"), HttpStatusCode.NotFound)
        }
    }

    post("/api/admin/test-command") {
        if (!call.requireAdmin()) return@post
        val request = adminJson.decodeFromString<TestCommandRequest>(call.receiveText())
        val plainText = request.plainText.trim()
        if (plainText.isBlank()) {
            call.respondJson(AdminResponse<Unit>(ok = false, error = "指令不能为空"), HttpStatusCode.BadRequest)
            return@post
        }

        val message = commandRouter.call(
            MessageSender(
                groupOpenId = idOf(request.groupId.blankToNull() ?: "web-admin-group"),
                senderName = request.senderName.blankToNull() ?: "WebAdmin",
                senderOpenId = idOf(request.senderId.blankToNull() ?: "web-admin-user"),
                message = plainText,
                plainText = plainText,
            )
        )

        call.respondJson(AdminResponse(data = MessagePreview.from(message)))
    }

    get("/api/admin/config") {
        if (!call.requireAdmin()) return@get
        call.respondJson(AdminResponse(data = configService.view()))
    }

    get("/api/admin/system") {
        if (!call.requireAdmin()) return@get
        call.respondJson(AdminResponse(data = systemService.view()))
    }

    get("/api/admin/exceptions") {
        if (!call.requireAdmin()) return@get
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 100
        call.respondJson(AdminResponse(data = exceptionRepository.list(limit)))
    }

    get("/api/admin/plugins") {
        if (!call.requireAdmin()) return@get
        call.respondJson(AdminResponse(data = CommandPlugins.views()))
    }

    post("/api/admin/plugins/reload") {
        if (!call.requireAdmin()) return@post
        runCatching { CommandPlugins.reloadAll() }
            .onSuccess { call.respondJson(AdminResponse(data = it)) }
            .onFailure { error ->
                exceptionRepository.record(error, "admin.plugins.reload-all")
                call.respondJson(AdminResponse<Unit>(ok = false, error = error.message ?: "插件重载失败"), HttpStatusCode.BadRequest)
            }
    }

    post("/api/admin/plugins/upload") {
        if (!call.requireAdmin()) return@post
        try {
            var installed: cn.luorenmu.command.plugin.CommandPluginView? = null
            call.receiveMultipart().forEachPart { part ->
                try {
                    if (part is PartData.FileItem && installed == null) {
                        val fileName = part.originalFileName ?: error("插件文件名不能为空")
                        installed = part.streamProvider().use { input -> CommandPlugins.installJar(fileName, input) }
                    }
                } finally {
                    part.dispose()
                }
            }
            call.respondJson(AdminResponse(data = installed ?: error("请选择插件 JAR")))
        } catch (error: Throwable) {
            exceptionRepository.record(error, "admin.plugins.upload")
            call.respondJson(AdminResponse<Unit>(ok = false, error = error.message ?: "插件加载失败"), HttpStatusCode.BadRequest)
        }
    }

    post("/api/admin/plugin-disabled-replies/{id}") {
        if (!call.requireAdmin()) return@post
        val id = call.parameters["id"].orEmpty()
        try {
            val request = adminJson.decodeFromString<PluginDisabledReplyRequest>(call.receiveText())
            call.respondJson(AdminResponse(data = CommandPlugins.setDisabledReply(id, request.reply)))
        } catch (error: Throwable) {
            exceptionRepository.record(error, "admin.plugins.disabled-reply", "plugin=$id")
            call.respondJson(AdminResponse<Unit>(ok = false, error = error.message ?: "停用回复保存失败"), HttpStatusCode.BadRequest)
        }
    }

    post("/api/admin/plugins/{id}/{action}") {
        if (!call.requireAdmin()) return@post
        val id = call.parameters["id"].orEmpty()
        val action = call.parameters["action"].orEmpty()
        runCatching {
            when (action) {
                "enable" -> CommandPlugins.enable(id)
                "disable" -> CommandPlugins.disable(id)
                "reload" -> CommandPlugins.reload(id)
                else -> error("不支持的插件操作: $action")
            }
        }.onSuccess { call.respondJson(AdminResponse(data = it)) }
            .onFailure { error ->
                exceptionRepository.record(error, "admin.plugins.$action", "plugin=$id")
                call.respondJson(AdminResponse<Unit>(ok = false, error = error.message ?: "插件操作失败"), HttpStatusCode.BadRequest)
            }
    }

    get("/api/admin/database/tables") {
        if (!call.requireAdmin()) return@get
        runCatching { databaseService.tables() }
            .onSuccess { call.respondJson(AdminResponse(data = it)) }
            .onFailure {
                exceptionRepository.record(it, "admin.database.tables")
                call.respondJson(AdminResponse<Unit>(ok = false, error = it.message ?: "数据库不可用"), HttpStatusCode.ServiceUnavailable)
            }
    }

    get("/api/admin/database/tables/{table}") {
        if (!call.requireAdmin()) return@get
        val table = call.parameters["table"].orEmpty()
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50
        val offset = call.request.queryParameters["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        runCatching { databaseService.page(table, limit, offset) }
            .onSuccess { call.respondJson(AdminResponse(data = it)) }
            .onFailure {
                exceptionRepository.record(it, "admin.database.page", "table=$table; limit=$limit; offset=$offset")
                call.respondJson(AdminResponse<Unit>(ok = false, error = it.message ?: "读取数据库失败"), HttpStatusCode.BadRequest)
            }
    }

    put("/api/admin/database/tables/{table}/row") {
        if (!call.requireAdmin()) return@put
        val table = call.parameters["table"].orEmpty()
        runCatching {
            databaseService.update(table, adminJson.decodeFromString<AdminRowUpdate>(call.receiveText()))
        }.onSuccess { updated ->
            if (updated) call.respondJson(AdminResponse(data = true))
            else call.respondJson(AdminResponse<Unit>(ok = false, error = "记录不存在或未更新"), HttpStatusCode.NotFound)
        }.onFailure { error ->
            exceptionRepository.record(error, "admin.database.update", "table=$table")
            call.respondJson(AdminResponse<Unit>(ok = false, error = error.message ?: "更新失败"), HttpStatusCode.BadRequest)
        }
    }
}

private const val ADMIN_BACKGROUND_IMAGE = "https://img.cdn1.vip/i/6a8171cba817b_1786868171.webp"

private suspend fun ApplicationCall.respondAdminDashboard() {
    request.queryParameters[AdminAccessToken.QUERY_NAME]?.let { supplied ->
        if (!AdminAccessToken.matches(supplied)) {
            respondAdminLogin("访问令牌无效，请检查后重试。", HttpStatusCode.Unauthorized)
            return
        }
        startAdminSession(supplied.trim())
        respondRedirect(request.path())
        return
    }
    if (!hasValidAdminToken()) {
        respondAdminLogin()
        return
    }
    respond(
        FreeMarkerContent(
            "admin/dashboard.ftl",
            mapOf(
                "pageTitle" to "LoMu Control Center",
                "backgroundImageUrl" to ADMIN_BACKGROUND_IMAGE,
            ),
        )
    )
}

private fun ApplicationCall.startAdminSession(token: String) {
    response.cookies.append(
        Cookie(
            name = AdminAccessToken.COOKIE_NAME,
            value = token,
            path = "/",
            httpOnly = true,
            extensions = mapOf("SameSite" to "Strict"),
        )
    )
}

private suspend fun ApplicationCall.respondAdminLogin(
    error: String? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    respond(
        status,
        FreeMarkerContent(
            "admin/login.ftl",
            mapOf(
                "pageTitle" to "LoMu Control Center",
                "backgroundImageUrl" to ADMIN_BACKGROUND_IMAGE,
                "error" to error,
            ),
        ),
    )
}

private val adminDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    .withZone(ZoneId.systemDefault())

internal data class AdminSystemFragmentView(
    val startedAt: String,
    val serverTime: String,
    val uptime: String,
    val serverTimeMillis: Long,
    val uptimeMillis: Long,
    val operatingSystem: String,
    val processor: String,
    val java: String,
    val javaVendor: String,
    val jvmMemory: String,
    val systemMemory: String,
    val runtimeMode: String,
)

internal fun AdminSystemView.toFragmentView(): AdminSystemFragmentView {
    val serverInstant = Instant.parse(serverTime)
    val physicalMemory = if (systemMemoryTotalBytes != null && systemMemoryFreeBytes != null) {
        "${formatBytes(systemMemoryTotalBytes - systemMemoryFreeBytes)} / ${formatBytes(systemMemoryTotalBytes)}"
    } else {
        "不可用"
    }
    return AdminSystemFragmentView(
        startedAt = adminDateFormatter.format(Instant.parse(startedAt)),
        serverTime = adminDateFormatter.format(serverInstant),
        uptime = formatDuration(uptimeMillis),
        serverTimeMillis = serverInstant.toEpochMilli(),
        uptimeMillis = uptimeMillis,
        operatingSystem = "$osName $osVersion",
        processor = "$processors 核 · $osArch",
        java = "Java $javaVersion",
        javaVendor = javaVendor,
        jvmMemory = "${formatBytes(jvmMemoryUsedBytes)} / ${formatBytes(jvmMemoryMaxBytes)}",
        systemMemory = physicalMemory,
        runtimeMode = "$adapter · $databaseBackend · :$runtimePort",
    )
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = (milliseconds / 1_000).coerceAtLeast(0)
    val days = seconds / 86_400
    val hours = seconds % 86_400 / 3_600
    val minutes = seconds % 3_600 / 60
    val remainder = seconds % 60
    return buildList {
        if (days > 0) add("${days}天")
        if (days > 0 || hours > 0) add("${hours}时")
        if (days > 0 || hours > 0 || minutes > 0) add("${minutes}分")
        add("${remainder}秒")
    }.joinToString(" ")
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1_024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1_024 && unitIndex < units.lastIndex) {
        value /= 1_024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}

private suspend fun ApplicationCall.requireAdmin(): Boolean {
    val valid = hasValidAdminToken()
    if (!valid) respondJson(AdminResponse<Unit>(ok = false, error = "管理令牌无效"), HttpStatusCode.Unauthorized)
    return valid
}

private fun ApplicationCall.hasValidAdminToken(): Boolean {
    return AdminAccessToken.matches(request.header(AdminAccessToken.HEADER_NAME)) ||
        AdminAccessToken.matches(request.cookies[AdminAccessToken.COOKIE_NAME])
}

private fun idOf(value: String): ID {
    return StringID::class.java.getMethod("valueOf", String::class.java).invoke(null, value) as ID
}

@Serializable
private data class AdminResponse<T>(
    val ok: Boolean = true,
    val data: T? = null,
    val error: String? = null,
)

@Serializable
private data class UpdateCommandUsageRequest(
    val commandName: String,
    val nickname: String? = null,
    val groupId: String? = null,
    val senderId: String? = null,
)

@Serializable
private data class TestCommandRequest(
    val plainText: String,
    val groupId: String? = null,
    val senderId: String? = null,
    val senderName: String? = null,
)

@Serializable
private data class PluginDisabledReplyRequest(val reply: String)

@Serializable
private data class MessagePreview(
    val matched: Boolean,
    val elements: List<MessagePreviewElement>,
) {
    companion object {
        fun from(message: Message?): MessagePreview {
            if (message == null) return MessagePreview(matched = false, elements = emptyList())

            val elements = when (message) {
                is Messages -> message.toList()
                is Message.Element -> listOf(message)
            }

            return MessagePreview(
                matched = true,
                elements = elements.map { MessagePreviewElement.from(it) },
            )
        }
    }
}

@Serializable
internal data class MessagePreviewElement(
    val type: String,
    val text: String? = null,
    val imageUrl: String? = null,
    val raw: String,
) {
    companion object {
        fun from(element: Message.Element): MessagePreviewElement {
            return when (element) {
                is PlainText -> MessagePreviewElement(type = "text", text = element.text, raw = element.toString())
                is OfflineResourceImage -> MessagePreviewElement(
                    type = "image",
                    imageUrl = element.resource.readNoArgString("getPath")
                        ?.let(Path::of)
                        ?.toResourceUrl(),
                    raw = element.toString(),
                )
                is OfflineFileImage -> MessagePreviewElement(
                    type = "image",
                    imageUrl = element.file.toPath().toResourceUrl(),
                    raw = element.toString(),
                )
                is OfflinePathImage -> MessagePreviewElement(
                    type = "image",
                    imageUrl = element.path.toResourceUrl(),
                    raw = element.toString(),
                )
                is OfflineURIImage -> MessagePreviewElement(
                    type = "image",
                    imageUrl = element.uri.toString(),
                    raw = element.toString(),
                )
                else -> MessagePreviewElement(
                    type = "text",
                    text = element.extractReadableText(),
                    raw = element.toString(),
                )
            }
        }
    }
}

private suspend inline fun <reified T> ApplicationCall.respondJson(
    response: AdminResponse<T>,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    respondText(adminJson.encodeToString(response), ContentType.Application.Json, status)
}

private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun Path.toResourceUrl(): String? {
    val resourcesRoot = PathUtils.resourcesPathResolve().toAbsolutePath().normalize()
    val absolutePath = toAbsolutePath().normalize()
    if (!absolutePath.startsWith(resourcesRoot)) return null
    return "/resources/" + resourcesRoot.relativize(absolutePath).toString().replace('\\', '/')
}

private fun Message.Element.extractReadableText(): String {
    readNoArgString("getText")?.let { return it }
    val markdown = readNoArgValue("getMarkdown")
    if (markdown != null) {
        markdown.readNoArgString("getContent")?.let { return it }
        markdown.readNoArgString("getText")?.let { return it }
        markdown.readNoArgString("getTemplateId")?.let { return "templateId=$it" }
    }
    return toString()
}

private fun Any.readNoArgString(methodName: String): String? {
    return readNoArgValue(methodName)?.toString()?.takeIf { it.isNotBlank() }
}

private fun Any.readNoArgValue(methodName: String): Any? {
    return runCatching {
        javaClass.methods.firstOrNull { method ->
            method.name == methodName && method.parameterCount == 0
        }?.also { it.trySetAccessible() }?.invoke(this)
    }.getOrNull()
}
