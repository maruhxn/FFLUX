package com.maruhxn.fflux.adapter

import com.maruhxn.fflux.broadcast.application.provided.BroadcastFinder
import com.maruhxn.fflux.realtime.application.required.LiveBroadcastFinder
import org.springframework.stereotype.Component

/** realtime이 후원 알림을 보내기 전에 방송이 LIVE인지 broadcast 모듈에서 본다. */
@Component
class LiveBroadcastAdapter(
    private val broadcastFinder: BroadcastFinder,
) : LiveBroadcastFinder {
    override fun isLive(broadcastId: Long): Boolean = broadcastFinder.isLive(broadcastId)
}
