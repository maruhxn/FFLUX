package com.maruhxn.fflux.chat.application

import com.maruhxn.fflux.chat.application.provided.ChatFinder
import com.maruhxn.fflux.chat.application.provided.ChatResult
import com.maruhxn.fflux.chat.application.provided.ChatWriter
import com.maruhxn.fflux.chat.application.provided.WriteChatCommand
import com.maruhxn.fflux.chat.application.required.ChatRepository
import com.maruhxn.fflux.chat.application.required.LiveChatTargetFinder
import com.maruhxn.fflux.chat.domain.Chat
import com.maruhxn.fflux.chat.domain.ChatContent
import com.maruhxn.fflux.common.event.ChatPosted
import com.maruhxn.fflux.common.event.EventPublisherPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional
class ChatService(
    private val chatRepository: ChatRepository,
    private val liveChatTargetFinder: LiveChatTargetFinder,
    private val eventPublisherPort: EventPublisherPort,
    private val clock: Clock,
) : ChatWriter, ChatFinder {

    /** 커밋 뒤 realtime이 [ChatPosted]를 받아 그 방송 구독자에게 보낸다. */
    override fun write(command: WriteChatCommand): Long {
        val content = ChatContent.of(command.content)
        val live = liveChatTargetFinder.isLive(command.broadcastId)
        val chat = chatRepository.save(
            Chat.post(command.broadcastId, live, command.authorId, command.authorNickname, content, LocalDateTime.now(clock)),
        )
        eventPublisherPort.publish(ChatPosted(chat.broadcastId, chat.id, chat.authorNickname, chat.content.value))
        return chat.id
    }

    /** 채팅은 LIVE 방송에서만 보인다(다시보기에는 숨긴다). */
    @Transactional(readOnly = true)
    override fun findRecent(broadcastId: Long): List<ChatResult> {
        if (!liveChatTargetFinder.isLive(broadcastId)) return emptyList()
        return chatRepository.findTop50ByBroadcastIdOrderByIdDesc(broadcastId).asReversed()
            .map { ChatResult(it.id, it.authorNickname, it.content.value) }
    }
}
