package com.maruhxn.fflux.realtime.port.event

import com.maruhxn.fflux.common.event.BroadcastStatusChanged
import com.maruhxn.fflux.common.event.BroadcastUpdated
import com.maruhxn.fflux.realtime.application.provided.BroadcastEventSender
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** 방송 모듈의 이벤트를 커밋 뒤에 받아 그 방송 구독자에게 SSE로 보낸다. */
@Component
class BroadcastEventListener(
    private val broadcastEventSender: BroadcastEventSender,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: BroadcastStatusChanged) {
        broadcastEventSender.send(event.broadcastId, "broadcast-status", StatusMessage(event.status))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: BroadcastUpdated) {
        broadcastEventSender.send(event.broadcastId, "broadcast-updated", UpdatedMessage(event.title, event.description))
    }

    private data class StatusMessage(val status: String)

    private data class UpdatedMessage(val title: String, val description: String?)
}
