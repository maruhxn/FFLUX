package com.maruhxn.fflux.adapter

import com.maruhxn.fflux.broadcast.application.provided.BroadcastFinder
import com.maruhxn.fflux.comment.application.required.ReplayFinder
import org.springframework.stereotype.Component

/** comment 모듈이 댓글을 달 방송이 다시보기인지 broadcast 모듈에서 본다. */
@Component
class ReplayAdapter(
    private val broadcastFinder: BroadcastFinder,
) : ReplayFinder {
    /**
     * 없거나 삭제된 방송이면 상세 조회가 404를 던진다.
     * READY 통지가 종료보다 먼저 올 수 있으므로(순서 뒤바뀜) 끝난 방송인지도 따로 본다.
     */
    override fun isReplay(broadcastId: Long): Boolean =
        broadcastFinder.find(broadcastId, memberId = null).recording?.status == READY &&
            broadcastFinder.findEnded(listOf(broadcastId)).isNotEmpty()

    private companion object {
        const val READY = "READY"
    }
}
