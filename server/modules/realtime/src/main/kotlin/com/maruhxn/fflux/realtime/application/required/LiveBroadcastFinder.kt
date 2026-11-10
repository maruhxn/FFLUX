package com.maruhxn.fflux.realtime.application.required

/** 방송이 지금 LIVE인지 본다(구현은 `app` 어댑터). */
interface LiveBroadcastFinder {
    /** 삭제되지 않은 LIVE 방송인지. 없거나 삭제됐으면 false. */
    fun isLive(broadcastId: Long): Boolean
}
