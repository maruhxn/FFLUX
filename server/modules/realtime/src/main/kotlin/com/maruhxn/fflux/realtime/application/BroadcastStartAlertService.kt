package com.maruhxn.fflux.realtime.application

import com.maruhxn.fflux.realtime.application.provided.BroadcastStartAlertSender
import com.maruhxn.fflux.realtime.application.required.FollowerFinder
import org.springframework.stereotype.Service

@Service
class BroadcastStartAlertService(
    private val followerFinder: FollowerFinder,
    private val memberEventHub: MemberEventHub,
) : BroadcastStartAlertSender {

    override fun send(broadcastId: Long, streamerId: Long, streamerNickname: String, title: String) {
        memberEventHub.send(
            followerFinder.followerIdsOf(streamerId),
            "broadcast-started",
            BroadcastStartedMessage(broadcastId, streamerNickname, title),
        )
    }

    private data class BroadcastStartedMessage(val broadcastId: Long, val streamerNickname: String, val title: String)
}
