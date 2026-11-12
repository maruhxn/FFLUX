package com.maruhxn.fflux.chat.application.required

import com.maruhxn.fflux.chat.domain.Chat

interface ChatRepository {
    fun save(chat: Chat): Chat

    /** 채팅을 행 잠금(`SELECT … FOR UPDATE`)으로 읽는다. */
    fun findForUpdateById(id: Long): Chat?

    /** 방송의 최근 50개, 최신순. */
    fun findTop50ByBroadcastIdOrderByIdDesc(broadcastId: Long): List<Chat>
}
