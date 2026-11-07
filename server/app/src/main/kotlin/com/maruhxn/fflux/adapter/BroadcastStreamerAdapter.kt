package com.maruhxn.fflux.adapter

import com.maruhxn.fflux.broadcast.application.provided.BroadcastFinder
import com.maruhxn.fflux.realtime.application.required.BroadcastStreamerFinder
import org.springframework.stereotype.Component

/** realtime 구독 전 방송 확인과 스트리머 조회를 broadcast 모듈에 맡긴다. */
@Component
class BroadcastStreamerAdapter(
    private val broadcastFinder: BroadcastFinder,
) : BroadcastStreamerFinder {
    override fun streamerIdOf(broadcastId: Long): Long = broadcastFinder.findStreamerId(broadcastId)
}
