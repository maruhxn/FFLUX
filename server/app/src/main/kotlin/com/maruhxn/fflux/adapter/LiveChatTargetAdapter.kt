package com.maruhxn.fflux.adapter

import com.maruhxn.fflux.broadcast.application.provided.BroadcastFinder
import com.maruhxn.fflux.chat.application.required.LiveChatTargetFinder
import org.springframework.stereotype.Component

/** chat 모듈이 채팅할 방송이 LIVE인지 broadcast 모듈에서 본다. */
@Component
class LiveChatTargetAdapter(
    private val broadcastFinder: BroadcastFinder,
) : LiveChatTargetFinder {
    /** `isLive`는 없는 방송에도 false라, 없거나 삭제된 방송의 404는 스트리머 조회로 먼저 낸다. */
    override fun isLive(broadcastId: Long): Boolean {
        broadcastFinder.findStreamerId(broadcastId)
        return broadcastFinder.isLive(broadcastId)
    }
}
