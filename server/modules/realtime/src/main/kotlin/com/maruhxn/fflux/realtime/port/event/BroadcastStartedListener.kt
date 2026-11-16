package com.maruhxn.fflux.realtime.port.event

import com.maruhxn.fflux.common.event.BroadcastStarted
import com.maruhxn.fflux.realtime.application.provided.BroadcastStartAlertSender
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** 방송 시작을 커밋 뒤에 받아 팔로워에게 알린다. 알림이 실패해도 LIVE 전환 훅이 실패로 응답하지 않도록 로그만 남긴다. */
@Component
class BroadcastStartedListener(
    private val broadcastStartAlertSender: BroadcastStartAlertSender,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: BroadcastStarted) {
        try {
            broadcastStartAlertSender.send(event.broadcastId, event.streamerId, event.streamerNickname, event.title)
        } catch (e: Exception) {
            log.warn("방송 시작 알림을 보내지 못했습니다. broadcastId={}", event.broadcastId, e)
        }
    }
}
