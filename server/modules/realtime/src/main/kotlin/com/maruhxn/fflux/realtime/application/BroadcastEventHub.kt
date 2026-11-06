package com.maruhxn.fflux.realtime.application

import com.maruhxn.fflux.common.error.NotFoundException
import com.maruhxn.fflux.realtime.application.provided.BroadcastEventSender
import com.maruhxn.fflux.realtime.application.provided.BroadcastEventSubscriber
import com.maruhxn.fflux.realtime.application.required.BroadcastExistenceChecker
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 방송별 SSE 구독자를 메모리에 들고 있는 허브(서버 1대 전제).
 * 연결이 끊긴 구독자는 보내기에 실패할 때 빠진다. heartbeat가 쉬는 연결에도 주기적으로 써서 죽은 구독자를 찾고,
 * 중간 네트워크(LTE NAT 등)가 쉬는 연결을 끊지 않게 한다.
 */
@Service
class BroadcastEventHub(
    private val broadcastExistenceChecker: BroadcastExistenceChecker,
) : BroadcastEventSubscriber, BroadcastEventSender {

    private val subscribers = ConcurrentHashMap<Long, MutableSet<SseEmitter>>()

    override fun subscribe(broadcastId: Long): SseEmitter {
        if (!broadcastExistenceChecker.exists(broadcastId)) throw NotFoundException("방송을 찾을 수 없습니다.")
        val emitter = SseEmitter(TIMEOUT.toMillis())
        // 빈 집합을 지우는 remove와 엇갈려도 지워진 집합에 넣지 않도록 키 단위로 원자적으로 넣는다.
        subscribers.compute(broadcastId) { _, set -> (set ?: ConcurrentHashMap.newKeySet()).apply { add(emitter) } }
        emitter.onCompletion { remove(broadcastId, emitter) }
        emitter.onError { remove(broadcastId, emitter) }
        // 제한 시간이 지나면 정상 종료한다. 클라이언트(EventSource)는 알아서 다시 연결한다.
        emitter.onTimeout { emitter.complete() }
        // 응답을 바로 시작해 클라이언트가 연결됐음을 알게 한다.
        trySend(broadcastId, emitter, SseEmitter.event().comment("connected"))
        return emitter
    }

    override fun send(broadcastId: Long, name: String, data: Any) {
        subscribers[broadcastId]?.forEach { emitter ->
            trySend(broadcastId, emitter, SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON))
        }
    }

    @Scheduled(fixedRate = HEARTBEAT_SECONDS, timeUnit = TimeUnit.SECONDS)
    fun heartbeat() {
        subscribers.forEach { (broadcastId, emitters) ->
            emitters.forEach { trySend(broadcastId, it, SseEmitter.event().comment("heartbeat")) }
        }
    }

    /** 열린 SSE 응답이 graceful shutdown을 붙잡지 않도록, 앱이 닫힐 때 모든 구독을 먼저 끝낸다(클라이언트는 다시 연결한다). */
    @EventListener(ContextClosedEvent::class)
    fun completeAll() {
        subscribers.values.forEach { emitters -> emitters.forEach { it.complete() } }
    }

    /** 연결이 끊겼으면 보내기가 실패한다. 그 구독자를 빼고 넘어간다. */
    private fun trySend(broadcastId: Long, emitter: SseEmitter, event: SseEmitter.SseEventBuilder) {
        try {
            emitter.send(event)
        } catch (e: Exception) {
            remove(broadcastId, emitter)
        }
    }

    private fun remove(broadcastId: Long, emitter: SseEmitter) {
        subscribers.computeIfPresent(broadcastId) { _, set -> set.apply { remove(emitter) }.ifEmpty { null } }
    }

    companion object {
        val TIMEOUT: Duration = Duration.ofMinutes(30)
        const val HEARTBEAT_SECONDS = 20L
    }
}
