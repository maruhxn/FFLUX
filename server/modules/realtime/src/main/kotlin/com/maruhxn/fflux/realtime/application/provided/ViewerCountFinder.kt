package com.maruhxn.fflux.realtime.application.provided

/** 방송별 지금 시청자 수(로그인한 회원 수, 스트리머 제외). */
interface ViewerCountFinder {
    /** 구독자가 없는 방송은 0이다. */
    fun viewerCounts(broadcastIds: Collection<Long>): Map<Long, Int>
}
