package com.maruhxn.fflux.realtime.application.required

/** 구독하려는 방송의 스트리머를 찾는다(구현은 `app` 어댑터). */
interface BroadcastStreamerFinder {
    /** 삭제되지 않은 방송의 스트리머 회원 ID. 없거나 삭제된 방송이면 404. */
    fun streamerIdOf(broadcastId: Long): Long
}
