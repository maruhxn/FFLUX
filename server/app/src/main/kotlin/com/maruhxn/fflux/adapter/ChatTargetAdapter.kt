package com.maruhxn.fflux.adapter

import com.maruhxn.fflux.broadcast.application.provided.BroadcastFinder
import com.maruhxn.fflux.chat.application.required.ChatTarget
import com.maruhxn.fflux.chat.application.required.ChatTargetFinder
import org.springframework.stereotype.Component

/** chat 모듈이 채팅할 방송의 스트리머와 LIVE 여부를 broadcast 모듈에서 본다. */
@Component
class ChatTargetAdapter(
    private val broadcastFinder: BroadcastFinder,
) : ChatTargetFinder {
    /** `isLive`는 없는 방송에도 false라, 없거나 삭제된 방송의 404는 스트리머 조회로 먼저 낸다. */
    override fun find(broadcastId: Long): ChatTarget =
        ChatTarget(streamerId = broadcastFinder.findStreamerId(broadcastId), live = broadcastFinder.isLive(broadcastId))

    override fun findLiveBroadcastIdOf(streamerId: Long): Long? = broadcastFinder.findLiveIdOf(streamerId)
}
