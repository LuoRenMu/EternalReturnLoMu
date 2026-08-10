package cn.luorenmu.api

import cn.luorenmu.command.CommandRouter
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.repository.StatisticsRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
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
import love.forte.simbot.message.OfflineURIImage
import love.forte.simbot.message.PlainText
import org.koin.java.KoinJavaComponent.inject
import java.nio.file.Path

private val adminJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Route.adminRouting() {
    val statisticsRepository: StatisticsRepository by inject(StatisticsRepository::class.java)
    val commandRouter = CommandRouter()

    get("/admin") {
        call.respondText(
            checkNotNull(AdminApiMarker::class.java.getResource("/static/admin_dashboard.html"))
                .readText(),
            ContentType.Text.Html,
        )
    }

    get("/api/admin/command-usages") {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 100
        call.respondJson(AdminResponse(data = statisticsRepository.listCommandUsages(limit)))
    }

    put("/api/admin/command-usages/{id}") {
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
}

private object AdminApiMarker

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
private data class MessagePreviewElement(
    val type: String,
    val text: String? = null,
    val imageUrl: String? = null,
    val raw: String,
) {
    companion object {
        fun from(element: Message.Element): MessagePreviewElement {
            return when (element) {
                is PlainText -> MessagePreviewElement(type = "text", text = element.text, raw = element.toString())
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
        }?.invoke(this)
    }.getOrNull()
}
