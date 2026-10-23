package cn.luorenmu.plugins.test

import cn.luorenmu.plugins.qqmedia.SimbotQQRuntime
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import love.forte.simbot.component.qguild.event.QGInteractionCreateEvent
import love.forte.simbot.event.EventListenerRegistrar
import love.forte.simbot.event.EventListenerRegistrationHandle
import love.forte.simbot.event.EventResult
import love.forte.simbot.event.listen

internal object TestButtonInteractionListener {
    private val log = KotlinLogging.logger {}

    fun register(): EventListenerRegistrationHandle? = SimbotQQRuntime.applicationOrNull()?.eventDispatcher?.let {
        register(it)
    }

    fun register(registrar: EventListenerRegistrar,
                 handler: TestButtonInteractionHandler = TestButtonInteractionHandler()): EventListenerRegistrationHandle {
        val registration = registrar.listen<QGInteractionCreateEvent> { event ->
            try {
                handler.handle(TestButtonInteraction.from(event.sourceEventEntity),
                    acknowledge = { code ->
                        event.respond(code)
                        log.info { "test-callback acknowledged: code=$code" }
                    },
                    sendReply = { group, eventId, reply ->
                        TestInteractionReplySender.send(event.bot, group, eventId, reply)
                        log.info { "test-callback reply sent" }
                    })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Do not log SDK exception bodies, private IDs, or signed image URLs.
                log.warn { "test-callback failed: ${error.javaClass.simpleName}" }
            }
            EventResult.empty()
        }
        log.info { "test-callback listener registered" }
        return registration
    }
}
