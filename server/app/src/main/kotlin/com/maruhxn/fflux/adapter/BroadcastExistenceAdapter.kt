package com.maruhxn.fflux.adapter

import com.maruhxn.fflux.broadcast.application.provided.BroadcastFinder
import com.maruhxn.fflux.realtime.application.required.BroadcastExistenceChecker
import org.springframework.stereotype.Component

/** realtime 구독 전 방송 확인을 broadcast 모듈에 맡긴다. */
@Component
class BroadcastExistenceAdapter(
    private val broadcastFinder: BroadcastFinder,
) : BroadcastExistenceChecker {
    override fun exists(broadcastId: Long): Boolean = broadcastFinder.exists(broadcastId)
}
