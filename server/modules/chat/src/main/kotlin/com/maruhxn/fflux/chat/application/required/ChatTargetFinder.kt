package com.maruhxn.fflux.chat.application.required

/** 채팅할 방송의 스트리머와 LIVE 여부를 본다(구현은 `app` 어댑터). */
interface ChatTargetFinder {
    /** 없거나 삭제된 방송이면 404(`NotFoundException`)를 던진다. */
    fun find(broadcastId: Long): ChatTarget

    /** 스트리머의 LIVE 방송 ID. 없으면 null. 채팅 금지·해제를 그 방송 구독자에게 알릴 때 쓴다. */
    fun findLiveBroadcastIdOf(streamerId: Long): Long?
}

/** [live]는 방송이 지금 LIVE인지다. */
data class ChatTarget(val streamerId: Long, val live: Boolean)
