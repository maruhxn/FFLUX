package com.maruhxn.fflux.broadcast.application

import com.maruhxn.fflux.broadcast.application.required.MediaPublisherKicker
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 종료 요청이 커밋된 뒤 같은 요청 스레드에서 송출 세션을 끊는다. 실패해도 방송은 이미 ENDED라 로그만 남긴다
 * (이후 오는 송출 끊김 훅은 no-op).
 */
@Component
class PublisherKickListener(
    private val mediaPublisherKicker: MediaPublisherKicker,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: BroadcastEndRequested) {
        try {
            mediaPublisherKicker.kickPublisher(event.mediaPath)
        } catch (e: Exception) {
            log.warn("송출 세션 kick 실패: id={} path={}", event.broadcastId, event.mediaPath, e)
        }
    }
}
