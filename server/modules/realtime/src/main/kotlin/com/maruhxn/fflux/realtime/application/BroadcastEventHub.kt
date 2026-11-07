package com.maruhxn.fflux.realtime.application

import com.maruhxn.fflux.realtime.application.provided.BroadcastEventSender
import com.maruhxn.fflux.realtime.application.provided.BroadcastEventSubscriber
import com.maruhxn.fflux.realtime.application.provided.ViewerCountFinder
import com.maruhxn.fflux.realtime.application.required.BroadcastStreamerFinder
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
 * 시청자 수는 구독 중인 로그인 회원을 회원 단위로 센 값이다. 연결이 드나들 때마다 보내면 폭주하므로 주기마다 바뀐 방송에만 보낸다.
 */
@Service
class BroadcastEventHub(
    private val broadcastStreamerFinder: BroadcastStreamerFinder,
) : BroadcastEventSubscriber, BroadcastEventSender, ViewerCountFinder {

    private val subscribers = ConcurrentHashMap<Long, MutableSet<Subscriber>>()

    /** 방송별로 마지막에 모두에게 보낸 시청자 수. 없으면 0을 보낸 것으로 본다. */
    private val sentViewerCounts = ConcurrentHashMap<Long, Int>()

    override fun subscribe(broadcastId: Long, memberId: Long?): SseEmitter {
        val streamerId = broadcastStreamerFinder.streamerIdOf(broadcastId)
        val subscriber = Subscriber(SseEmitter(TIMEOUT.toMillis()), viewerId = memberId?.takeIf { it != streamerId })
        val emitter = subscriber.emitter
        // 빈 집합을 지우는 remove와 엇갈려도 지워진 집합에 넣지 않도록 키 단위로 원자적으로 넣는다.
        subscribers.compute(broadcastId) { _, set -> (set ?: ConcurrentHashMap.newKeySet()).apply { add(subscriber) } }
        emitter.onCompletion { remove(broadcastId, subscriber) }
        emitter.onError { remove(broadcastId, subscriber) }
        // 제한 시간이 지나면 정상 종료한다. 클라이언트(EventSource)는 알아서 다시 연결한다.
        emitter.onTimeout { emitter.complete() }
        // 응답을 바로 시작해 클라이언트가 연결됐음을 알게 한다.
        trySend(broadcastId, subscriber, SseEmitter.event().comment("connected"))
        trySend(broadcastId, subscriber, viewerCountEvent(viewerCountOf(broadcastId)))
        return emitter
    }

    override fun send(broadcastId: Long, name: String, data: Any) {
        sendToAll(broadcastId, SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON))
    }

    override fun viewerCounts(broadcastIds: Collection<Long>): Map<Long, Int> =
        broadcastIds.associateWith(::viewerCountOf)

    @Scheduled(fixedRate = VIEWER_COUNT_SECONDS, timeUnit = TimeUnit.SECONDS)
    fun sendViewerCounts() {
        subscribers.keys.forEach { broadcastId ->
            val count = viewerCountOf(broadcastId)
            if ((sentViewerCounts.put(broadcastId, count) ?: 0) != count) sendToAll(broadcastId, viewerCountEvent(count))
        }
        sentViewerCounts.keys.retainAll(subscribers.keys)
    }

    @Scheduled(fixedRate = HEARTBEAT_SECONDS, timeUnit = TimeUnit.SECONDS)
    fun heartbeat() {
        subscribers.keys.forEach { sendToAll(it, SseEmitter.event().comment("heartbeat")) }
    }

    /** 열린 SSE 응답이 graceful shutdown을 붙잡지 않도록, 앱이 닫힐 때 모든 구독을 먼저 끝낸다(클라이언트는 다시 연결한다). */
    @EventListener(ContextClosedEvent::class)
    fun completeAll() {
        subscribers.values.forEach { set -> set.forEach { it.emitter.complete() } }
        subscribers.clear()
        sentViewerCounts.clear()
    }

    private fun viewerCountOf(broadcastId: Long): Int =
        subscribers[broadcastId].orEmpty().mapNotNullTo(HashSet()) { it.viewerId }.size

    private fun viewerCountEvent(count: Int) =
        SseEmitter.event().name("viewer-count").data(ViewerCountMessage(count), MediaType.APPLICATION_JSON)

    private fun sendToAll(broadcastId: Long, event: SseEmitter.SseEventBuilder) {
        subscribers[broadcastId]?.forEach { trySend(broadcastId, it, event) }
    }

    /** 연결이 끊겼으면 보내기가 실패한다. 그 구독자를 빼고 넘어간다. */
    private fun trySend(broadcastId: Long, subscriber: Subscriber, event: SseEmitter.SseEventBuilder) {
        try {
            subscriber.emitter.send(event)
        } catch (e: Exception) {
            remove(broadcastId, subscriber)
        }
    }

    private fun remove(broadcastId: Long, subscriber: Subscriber) {
        subscribers.computeIfPresent(broadcastId) { _, set -> set.apply { remove(subscriber) }.ifEmpty { null } }
    }

    /** [viewerId]는 시청자 수에 들어가는 회원 ID다. 비로그인이거나 그 방송의 스트리머면 null이다. */
    private class Subscriber(val emitter: SseEmitter, val viewerId: Long?)

    private data class ViewerCountMessage(val count: Int)

    companion object {
        val TIMEOUT: Duration = Duration.ofMinutes(30)
        const val HEARTBEAT_SECONDS = 20L
        const val VIEWER_COUNT_SECONDS = 5L
    }
}
