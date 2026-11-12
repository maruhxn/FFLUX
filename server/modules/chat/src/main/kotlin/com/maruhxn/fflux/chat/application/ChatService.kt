package com.maruhxn.fflux.chat.application

import com.maruhxn.fflux.chat.application.provided.ChatFinder
import com.maruhxn.fflux.chat.application.provided.ChatResult
import com.maruhxn.fflux.chat.application.provided.ChatWriter
import com.maruhxn.fflux.chat.application.provided.MyChatRestriction
import com.maruhxn.fflux.chat.application.provided.RecentChatsResult
import com.maruhxn.fflux.chat.application.provided.WriteChatCommand
import com.maruhxn.fflux.chat.application.required.ChatRepository
import com.maruhxn.fflux.chat.application.required.ChatRestrictionRepository
import com.maruhxn.fflux.chat.application.required.ChatTargetFinder
import com.maruhxn.fflux.chat.domain.Chat
import com.maruhxn.fflux.chat.domain.ChatRestriction
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
    private val chatTargetFinder: ChatTargetFinder,
    private val chatRestrictionRepository: ChatRestrictionRepository,
    private val eventPublisherPort: EventPublisherPort,
    private val clock: Clock,
) : ChatWriter, ChatFinder {

    /** 판정 순서는 본문 400 → 방송 없음 404 → 채팅 금지 403 → LIVE 아님 409. 커밋 뒤 realtime이 [ChatPosted]를 받아 그 방송 구독자에게 보낸다. */
    override fun write(command: WriteChatCommand): Long {
        val content = ChatContent.of(command.content)
        val target = chatTargetFinder.find(command.broadcastId)
        val restriction = chatRestrictionRepository.findByStreamerIdAndMemberId(target.streamerId, command.authorId)
        val chat = chatRepository.save(
            Chat.post(command.broadcastId, target.live, restriction, command.authorId, command.authorNickname, content, LocalDateTime.now(clock)),
        )
        eventPublisherPort.publish(ChatPosted(chat.broadcastId, chat.id, chat.authorId, chat.authorNickname, chat.content.value))
        return chat.id
    }

    /** 채팅은 LIVE 방송에서만 보인다(다시보기에는 숨긴다). */
    @Transactional(readOnly = true)
    override fun findRecent(broadcastId: Long, memberId: Long?): RecentChatsResult {
        val target = chatTargetFinder.find(broadcastId)
        val restriction = memberId?.let { activeRestriction(target.streamerId, it, LocalDateTime.now(clock)) }?.let { MyChatRestriction(it.until) }
        val chats = if (target.live) chatRepository.findTop50ByBroadcastIdOrderByIdDesc(broadcastId).asReversed().map { it.toResult() } else emptyList()
        return RecentChatsResult(chats, restriction)
    }

    private fun activeRestriction(streamerId: Long, memberId: Long, now: LocalDateTime): ChatRestriction? =
        chatRestrictionRepository.findByStreamerIdAndMemberId(streamerId, memberId)?.takeIf { it.isActive(now) }

    /** 가린 채팅은 닉네임과 본문을 가린다. */
    private fun Chat.toResult(): ChatResult {
        val hidden = hiddenAt != null
        return ChatResult(id, authorId, authorNickname.takeUnless { hidden }, content.value.takeUnless { hidden }, hidden)
    }

}
