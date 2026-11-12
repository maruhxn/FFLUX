package com.maruhxn.fflux.realtime.port.event

import com.maruhxn.fflux.common.event.ChatHidden
import com.maruhxn.fflux.common.event.ChatPosted
import com.maruhxn.fflux.common.event.ChatRestrictionChanged
import com.maruhxn.fflux.realtime.application.provided.BroadcastEventSender
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.LocalDateTime

/** 채팅 작성을 커밋 뒤에 받아 그 방송 구독자에게 SSE `chat`으로 보낸다. 모양은 입장 시 목록의 항목과 같다. */
@Component
class ChatEventListener(
    private val broadcastEventSender: BroadcastEventSender,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: ChatPosted) {
        broadcastEventSender.send(event.broadcastId, "chat", ChatMessage(event.chatId, event.authorId, event.authorNickname, event.content, hidden = false))
    }

    /** 스트리머가 가린 채팅. 화면은 같은 [ChatHiddenMessage.id]의 채팅을 가린 자리로 바꾼다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: ChatHidden) {
        broadcastEventSender.send(event.broadcastId, "chat-hidden", ChatHiddenMessage(event.chatId))
    }

    private data class ChatHiddenMessage(val id: Long)

    /** 채팅 금지·해제. 모든 구독자가 받지만 [ChatRestrictionMessage.memberId]가 자기인 화면만 입력 칸을 막거나 연다. 닉네임은 담지 않는다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: ChatRestrictionChanged) {
        broadcastEventSender.send(event.broadcastId, "chat-restriction", ChatRestrictionMessage(event.memberId, event.restricted, event.until))
    }

    private data class ChatRestrictionMessage(val memberId: Long, val restricted: Boolean, val until: LocalDateTime?)

    private data class ChatMessage(val id: Long, val authorId: Long, val authorNickname: String, val content: String, val hidden: Boolean)
}
