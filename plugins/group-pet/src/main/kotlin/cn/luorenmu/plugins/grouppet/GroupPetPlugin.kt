package cn.luorenmu.plugins.grouppet

import cn.luorenmu.Adapter
import cn.luorenmu.currentAdapter
import cn.luorenmu.command.entity.MessageSender
import cn.luorenmu.command.plugin.CommandPlugin
import cn.luorenmu.command.plugin.CommandPlugins
import cn.luorenmu.common.util.PathUtils
import cn.luorenmu.common.util.DatabaseManager
import org.koin.java.KoinJavaComponent.inject
import cn.luorenmu.plugins.qqmedia.QQProactiveGroupSender
import cn.luorenmu.plugins.qqmedia.SimbotQQRuntime
import cn.luorenmu.plugins.qqmedia.BackgroundPolls
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import love.forte.simbot.common.id.StringID.Companion.ID
import love.forte.simbot.message.Image
import love.forte.simbot.message.Message

import love.forte.simbot.component.qguild.event.*
import love.forte.simbot.event.EventListenerRegistrationHandle
import love.forte.simbot.event.EventResult
import love.forte.simbot.event.listen
import java.time.Instant

class GroupPetPlugin : CommandPlugin {
    override val id = "group-pet"
    override val name = "群宠战绩订阅"
    override val version = "1.2.7"
    private val databaseManager: DatabaseManager by inject(DatabaseManager::class.java)
    private val repository by lazy {
        GroupPetRepository(PathUtils.pathResolve(paths = arrayOf("data", "group-pet.db")), databaseManager)
    }
    private val listeners = mutableListOf<EventListenerRegistrationHandle>()
    private val permissionGate = GroupPetPermissionGate()
    private val service by lazy {
        val upstream = DakGroupPetSource()
        val staggered = object : GroupPetSource {
            override suspend fun latest(nickname: String) = BackgroundPolls.query { upstream.latest(nickname) }
            override suspend fun forSubscription(nickname: String) = upstream.forSubscription(nickname)
        }
        GroupPetService(repository,
            SharedGroupPetSource(staggered), ::renderMatch, ::sendMatch, permission = permissionGate::query)
    }
    override val commands = listOf<cn.luorenmu.command.CommandEvent>(GroupPetCommand { service })
    private val log = KotlinLogging.logger {}
    private var scope: CoroutineScope? = null
    private val sendMutex = Mutex()
    private var lastSendNanos: Long? = null

    @Synchronized
    override fun onEnable() {
        if (currentAdapter != Adapter.QG_BOT || scope != null) return
        val dispatcher = checkNotNull(SimbotQQRuntime.applicationOrNull()?.eventDispatcher)
        listeners += dispatcher.listen<QGGroupMsgReceiveEvent> { event ->
            permission(event, true); EventResult.empty()
        }
        listeners += dispatcher.listen<QGGroupMsgRejectEvent> { event ->
            permission(event, false); EventResult.empty()
        }
        listeners += dispatcher.listen<QGGroupDelRobotEvent> { event ->
            permission(event, false); EventResult.empty()
        }
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { worker ->
            worker.launch {
                while (isActive) {
                    BackgroundPolls.awaitNext(BackgroundPolls.Kind.PLAYER)
                    try {
                        val count = service.tick()
                        if (count > 0) log.info { "群宠主动消息已发送：$count 条" }
                    } catch (error: Exception) {
                        currentCoroutineContext().ensureActive()
                        log.warn { "群宠扫描失败：${error.javaClass.simpleName}" }
                    }
                }
            }
        }
    }

    @Synchronized
    override fun onDisable() {
        listeners.forEach { it.dispose() }
        listeners.clear()
        scope?.cancel()
        scope = null
    }

    private fun permission(event: QGGroupManagementEvent, allowed: Boolean) {
        val data = event.sourceEventEntity
        val timestamp = data.timestamp.toLongOrNull()?.let { it * 1000 }
            ?: runCatching { Instant.parse(data.timestamp).toEpochMilli() }.getOrNull()
        if (timestamp == null && allowed) return
        repository.updatePermission(data.groupOpenid, allowed, timestamp ?: System.currentTimeMillis())
    }

    private suspend fun renderMatch(snapshot: PetSnapshot): Message {
        val match = checkNotNull(snapshot.latest)
        val command = CommandPlugins.commands()["查询对局"]?.commandEvent ?: error("MatchPluginUnavailable")
        val text = "/查询对局 ${match.id} ${snapshot.nickname} ${match.seasonId}"
        val sender = MessageSender("group-pet-worker".ID, "群宠", "group-pet-worker".ID, text, text)
        val reply = command.listen(sender, mapOf("gameId" to match.id.toString(), "nickname" to snapshot.nickname,
            "seasonId" to match.seasonId.toString()))
        check(reply is Image) { "MatchImageUnavailable" }
        return reply
    }

    private suspend fun sendMatch(group: String, message: Message) = sendProactive(group, message)

    private suspend fun sendProactive(group: String, message: Message, expiresAt: Long = Long.MAX_VALUE) = sendMutex.withLock {
        // Keep this plugin below both the bot's unverified quota and each group's 20/min quota.
        lastSendNanos?.let { previous ->
            val remaining = 6_100 - (System.nanoTime() - previous) / 1_000_000
            if (remaining > 0) delay(remaining)
        }
        check(System.currentTimeMillis() < expiresAt) { "PushExpired" }
        lastSendNanos = System.nanoTime()
        QQProactiveGroupSender.send(SimbotQQRuntime.requireBot(), group, message)
    }
}
