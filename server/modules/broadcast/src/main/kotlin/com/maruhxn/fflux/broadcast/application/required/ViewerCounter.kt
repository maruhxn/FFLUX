package com.maruhxn.fflux.broadcast.application.required

/** 방송별 지금 시청자 수를 세는 포트(구현은 `app` 어댑터가 realtime 모듈에 맡긴다). */
interface ViewerCounter {
    /** 구독자가 없는 방송은 0이다. */
    fun viewerCounts(broadcastIds: Collection<Long>): Map<Long, Int>
}
