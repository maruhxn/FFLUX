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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 방송별 SSE 허브. 구독자마다 시청자 수에 들어가는 회원 ID를 붙여 둔다(비로그인이거나 그 방송의 스트리머면 null).
 * 시청자 수는 구독 중인 로그인 회원을 회원 단위로 센 값이다. 연결이 드나들 때마다 보내면 폭주하므로 주기마다 바뀐 방송에만 보낸다.
 */
@Service
class BroadcastEventHub(
    private val broadcastStreamerFinder: BroadcastStreamerFinder,
) : BroadcastEventSubscriber, BroadcastEventSender, ViewerCountFinder {

    private val subscribers = SseSubscribers<Long, Long?>()

    /** 방송별로 마지막에 모두에게 보낸 시청자 수. 없으면 0을 보낸 것으로 본다. */
    private val sentViewerCounts = ConcurrentHashMap<Long, Int>()

    override fun subscribe(broadcastId: Long, memberId: Long?): SseEmitter {
        val streamerId = broadcastStreamerFinder.streamerIdOf(broadcastId)
        return subscribers.add(broadcastId, memberId?.takeIf { it != streamerId }) {
            listOf(viewerCountEvent(viewerCountOf(broadcastId)))
        }
    }

    override fun send(broadcastId: Long, name: String, data: Any) {
        subscribers.send(broadcastId, SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON))
    }

    override fun viewerCounts(broadcastIds: Collection<Long>): Map<Long, Int> =
        broadcastIds.associateWith(::viewerCountOf)

    @Scheduled(fixedRate = VIEWER_COUNT_SECONDS, timeUnit = TimeUnit.SECONDS)
    fun sendViewerCounts() {
        subscribers.keys.forEach { broadcastId ->
            val count = viewerCountOf(broadcastId)
            if ((sentViewerCounts.put(broadcastId, count) ?: 0) != count) subscribers.send(broadcastId, viewerCountEvent(count))
        }
        sentViewerCounts.keys.retainAll(subscribers.keys)
    }

    @Scheduled(fixedRate = HEARTBEAT_SECONDS, timeUnit = TimeUnit.SECONDS)
    fun heartbeat() = subscribers.heartbeat()

    @EventListener(ContextClosedEvent::class)
    fun completeAll() {
        subscribers.completeAll()
        sentViewerCounts.clear()
    }

    private fun viewerCountOf(broadcastId: Long): Int = subscribers.tagsOf(broadcastId).filterNotNullTo(HashSet()).size

    private fun viewerCountEvent(count: Int) =
        SseEmitter.event().name("viewer-count").data(ViewerCountMessage(count), MediaType.APPLICATION_JSON)

    private data class ViewerCountMessage(val count: Int)

    companion object {
        /** 끊긴 연결은 보통 두 번째 쓰기에서 실패하므로, 시청자가 나간 것은 이 주기의 약 두 배 뒤에 반영된다. */
        const val HEARTBEAT_SECONDS = 5L
        const val VIEWER_COUNT_SECONDS = 5L
    }
}
