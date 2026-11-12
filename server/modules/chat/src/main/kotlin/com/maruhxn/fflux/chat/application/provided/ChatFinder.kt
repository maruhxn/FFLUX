package com.maruhxn.fflux.chat.application.provided

import java.time.LocalDateTime

/** 방송에 입장할 때 읽는 채팅. 그 뒤의 채팅은 SSE `chat`으로 받는다. */
interface ChatFinder {
    /**
     * LIVE 방송의 최근 50개를 오래된 것부터 준다. LIVE가 아니면 빈 목록, 없거나 삭제된 방송이면 404.
     * 로그인 회원([memberId])이 그 방송 스트리머에게 채팅 금지돼 있으면 그 상태도 담는다.
     */
    fun findRecent(broadcastId: Long, memberId: Long?): RecentChatsResult
}

/** [restriction]은 조회한 회원의 채팅 금지 상태이고, 금지 중이 아니거나 비로그인이면 null이다. */
data class RecentChatsResult(val chats: List<ChatResult>, val restriction: MyChatRestriction?)

/** [until]이 null이면 스트리머가 풀 때까지다. */
data class MyChatRestriction(val until: LocalDateTime?)

/**
 * SSE `chat` 이벤트와 같은 모양이다. 화면은 [id]로 목록과 SSE의 중복을 거른다.
 * 가린 채팅([hidden])은 [authorNickname]과 [content]를 null로 가린다.
 */
data class ChatResult(
    val id: Long,
    val authorId: Long,
    val authorNickname: String?,
    val content: String?,
    val hidden: Boolean,
)
