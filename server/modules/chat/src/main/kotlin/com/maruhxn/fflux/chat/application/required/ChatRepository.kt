package com.maruhxn.fflux.chat.application.required

import com.maruhxn.fflux.chat.domain.Chat

interface ChatRepository {
    fun save(chat: Chat): Chat

    /** 방송의 최근 50개, 최신순. */
    fun findTop50ByBroadcastIdOrderByIdDesc(broadcastId: Long): List<Chat>
}
