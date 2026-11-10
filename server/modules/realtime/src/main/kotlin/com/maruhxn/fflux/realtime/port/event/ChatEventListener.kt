package com.maruhxn.fflux.realtime.port.event

import com.maruhxn.fflux.common.event.ChatPosted
import com.maruhxn.fflux.realtime.application.provided.BroadcastEventSender
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** 채팅 작성을 커밋 뒤에 받아 그 방송 구독자에게 SSE `chat`으로 보낸다. 모양은 입장 시 목록의 항목과 같다. */
@Component
class ChatEventListener(
    private val broadcastEventSender: BroadcastEventSender,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: ChatPosted) {
        broadcastEventSender.send(event.broadcastId, "chat", ChatMessage(event.chatId, event.authorNickname, event.content))
    }

    private data class ChatMessage(val id: Long, val authorNickname: String, val content: String)
}
