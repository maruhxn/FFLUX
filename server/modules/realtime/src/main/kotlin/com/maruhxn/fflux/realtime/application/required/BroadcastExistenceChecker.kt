package com.maruhxn.fflux.realtime.application.required

/** 구독하려는 방송이 있는지 확인한다(구현은 `app` 어댑터). */
interface BroadcastExistenceChecker {
    /** 삭제되지 않은 방송이 있는지. */
    fun exists(broadcastId: Long): Boolean
}
