package com.maruhxn.fflux.chat.application

import com.maruhxn.fflux.chat.application.provided.ChatModerator
import com.maruhxn.fflux.chat.application.provided.ChatRestrictionResult
import com.maruhxn.fflux.chat.application.required.ChatRepository
import com.maruhxn.fflux.chat.application.required.ChatRestrictionRepository
import com.maruhxn.fflux.chat.application.required.ChatTargetFinder
import com.maruhxn.fflux.chat.domain.Chat
import com.maruhxn.fflux.chat.domain.ChatRestrictionDuration
import com.maruhxn.fflux.common.error.NotFoundException
import com.maruhxn.fflux.common.event.ChatHidden
import com.maruhxn.fflux.common.event.ChatRestrictionChanged
import com.maruhxn.fflux.common.event.EventPublisherPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional
class ChatModerationService(
    private val chatRepository: ChatRepository,
    private val chatTargetFinder: ChatTargetFinder,
    private val chatRestrictionRepository: ChatRestrictionRepository,
    private val eventPublisherPort: EventPublisherPort,
    private val clock: Clock,
) : ChatModerator {

    /** 실제로 가렸을 때만 커밋 뒤 realtime이 [ChatHidden]을 받아 SSE `chat-hidden`으로 보낸다. */
    override fun hide(chatId: Long, memberId: Long) {
        val chat = findForUpdateOrThrow(chatId)
        val target = chatTargetFinder.find(chat.broadcastId)
        if (chat.hide(memberId, target.streamerId, LocalDateTime.now(clock))) {
            eventPublisherPort.publish(ChatHidden(chat.broadcastId, chat.id))
        }
    }

    override fun restrict(chatId: Long, memberId: Long, duration: ChatRestrictionDuration) {
        val chat = findForUpdateOrThrow(chatId)
        val streamerId = chatTargetFinder.find(chat.broadcastId).streamerId
        val current = chatRestrictionRepository.findByStreamerIdAndMemberId(streamerId, chat.authorId)
        val restriction = chatRestrictionRepository.save(chat.restrictAuthor(memberId, streamerId, current, duration, LocalDateTime.now(clock)))
        notifyRestriction(streamerId, chat.authorId, restricted = true, until = restriction.until)
    }

    /** 실제로 풀었을 때만 알린다. */
    override fun lift(streamerId: Long, memberId: Long) {
        if (chatRestrictionRepository.deleteByStreamerIdAndMemberId(streamerId, memberId) > 0) {
            notifyRestriction(streamerId, memberId, restricted = false, until = null)
        }
    }

    @Transactional(readOnly = true)
    override fun findRestrictions(streamerId: Long): List<ChatRestrictionResult> {
        val now = LocalDateTime.now(clock)
        return chatRestrictionRepository.findAllByStreamerIdOrderByRestrictedAtDesc(streamerId)
            .filter { it.isActive(now) }
            .map { ChatRestrictionResult(it.memberId, it.memberNickname, it.until) }
    }

    /** 스트리머가 LIVE일 때만 그 방송 구독자에게 보낸다. 금지된 회원의 화면이 입력 칸을 막거나 연다. */
    private fun notifyRestriction(streamerId: Long, memberId: Long, restricted: Boolean, until: LocalDateTime?) {
        val liveId = chatTargetFinder.findLiveBroadcastIdOf(streamerId) ?: return
        eventPublisherPort.publish(ChatRestrictionChanged(liveId, memberId, restricted, until))
    }

    private fun findForUpdateOrThrow(chatId: Long): Chat =
        chatRepository.findForUpdateById(chatId) ?: throw NotFoundException("채팅을 찾을 수 없습니다.")
}
