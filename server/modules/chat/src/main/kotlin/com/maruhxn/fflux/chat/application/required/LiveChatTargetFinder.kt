package com.maruhxn.fflux.chat.application.required

/** 채팅할 방송이 LIVE인지 본다(구현은 `app` 어댑터). */
interface LiveChatTargetFinder {
    /** 방송이 지금 LIVE인지. 없거나 삭제된 방송이면 404(`NotFoundException`)를 던진다. */
    fun isLive(broadcastId: Long): Boolean
}
