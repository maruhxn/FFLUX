package com.maruhxn.fflux.realtime.application.provided

/** 방송이 LIVE가 되면 그 스트리머의 팔로워 중 구독 중인 회원에게 알린다. */
interface BroadcastStartAlertSender {
    fun send(broadcastId: Long, streamerId: Long, streamerNickname: String, title: String)
}
