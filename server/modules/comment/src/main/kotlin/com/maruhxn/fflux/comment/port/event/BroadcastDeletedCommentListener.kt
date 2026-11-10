package com.maruhxn.fflux.comment.port.event

import com.maruhxn.fflux.comment.application.provided.CommentModifier
import com.maruhxn.fflux.common.event.BroadcastDeleted
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** 방송이 삭제되면 커밋 뒤에 그 댓글을 숨긴다. 커밋이 끝난 트랜잭션이라 새 트랜잭션에서 쓴다. */
@Component
class BroadcastDeletedCommentListener(
    private val commentModifier: CommentModifier,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: BroadcastDeleted) {
        commentModifier.hideAll(event.broadcastId)
    }
}
