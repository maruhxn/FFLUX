package com.maruhxn.fflux.realtime.application

import com.maruhxn.fflux.realtime.application.provided.MemberEventSubscriber
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.TimeUnit

/** 회원별 SSE 허브. 한 회원이 여러 탭으로 구독하면 탭마다 받는다. */
@Service
class MemberEventHub : MemberEventSubscriber {

    private val subscribers = SseSubscribers<Long, Unit>()

    override fun subscribe(memberId: Long): SseEmitter = subscribers.add(memberId, Unit)

    /** 구독 중인 회원에게만 보낸다. */
    fun send(memberIds: Collection<Long>, name: String, data: Any) {
        val event = SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON)
        memberIds.forEach { subscribers.send(it, event) }
    }

    @Scheduled(fixedRate = BroadcastEventHub.HEARTBEAT_SECONDS, timeUnit = TimeUnit.SECONDS)
    fun heartbeat() = subscribers.heartbeat()

    @EventListener(ContextClosedEvent::class)
    fun completeAll() = subscribers.completeAll()
}
