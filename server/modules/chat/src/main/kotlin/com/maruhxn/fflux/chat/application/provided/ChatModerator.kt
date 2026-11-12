package com.maruhxn.fflux.chat.application.provided

import com.maruhxn.fflux.chat.domain.ChatRestrictionDuration
import java.time.LocalDateTime

/** 스트리머의 채팅 관리. 판단(스트리머인지, 상태)은 엔티티가 한다. */
interface ChatModerator {
    /** 채팅 가리기. 없거나 방송이 삭제됐으면 404, 방송의 스트리머가 아니면 403. 이미 가렸으면 그대로 성공한다. */
    fun hide(chatId: Long, memberId: Long)

    /**
     * 채팅 금지: 채팅 작성자를 그 방송 스트리머의 모든 방송에서 [duration] 동안 막는다. 이미 금지했으면 덮어쓴다.
     * 없거나 방송이 삭제됐으면 404, 방송의 스트리머가 아니면 403, 작성자가 스트리머 자신이면 409.
     */
    fun restrict(chatId: Long, memberId: Long, duration: ChatRestrictionDuration)

    /** 스트리머([streamerId])가 회원([memberId])의 채팅 금지를 푼다. 자기 금지만 다루고, 금지 중이 아니어도 성공한다. */
    fun lift(streamerId: Long, memberId: Long)

    /** 스트리머의 지금 금지 중인 회원들, 최근에 건 것부터. */
    fun findRestrictions(streamerId: Long): List<ChatRestrictionResult>
}

/** [until]이 null이면 스트리머가 풀 때까지다. [memberNickname]은 금지 당시 닉네임이다. */
data class ChatRestrictionResult(val memberId: Long, val memberNickname: String, val until: LocalDateTime?)
