package com.maruhxn.fflux.recording.port.event

import com.maruhxn.fflux.common.event.BroadcastDeleted
import com.maruhxn.fflux.recording.application.provided.RecordingStatusChanger
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** 방송이 삭제되면 커밋 뒤에 그 녹화를 숨긴다. 커밋이 끝난 트랜잭션이라 새 트랜잭션에서 쓴다. */
@Component
class BroadcastDeletedListener(
    private val recordingStatusChanger: RecordingStatusChanger,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: BroadcastDeleted) {
        recordingStatusChanger.hide(event.broadcastId)
    }
}
