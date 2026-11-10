package com.maruhxn.fflux.realtime.port.event

import com.maruhxn.fflux.common.event.DonationConfirmed
import com.maruhxn.fflux.realtime.application.provided.DonationAlertSender
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 후원 확정을 받아 LIVE 방송 구독자에게 알린다.
 * 후원 확정은 결제사 호출과 트랜잭션을 나누려고 조건부 UPDATE가 끝난(커밋된) 뒤 트랜잭션 밖에서 발행하므로 `fallbackExecution`으로 받는다.
 * 그래서 확정 요청 스레드에서 바로 돈다. 알림이 실패해도 이미 DONE인 확정이 실패로 응답하지 않도록 로그만 남긴다.
 */
@Component
class DonationEventListener(
    private val donationAlertSender: DonationAlertSender,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun on(event: DonationConfirmed) {
        try {
            donationAlertSender.send(event.broadcastId, event.donorNickname, event.amount, event.message)
        } catch (e: Exception) {
            log.warn("후원 알림을 보내지 못했습니다. broadcastId={}", event.broadcastId, e)
        }
    }
}
