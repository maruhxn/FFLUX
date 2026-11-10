package com.maruhxn.fflux.chat.application.provided

/** 방송에 입장할 때 읽는 채팅. 그 뒤의 채팅은 SSE `chat`으로 받는다. */
interface ChatFinder {
    /** LIVE 방송의 최근 50개를 오래된 것부터 준다. LIVE가 아니면 빈 목록, 없거나 삭제된 방송이면 404. */
    fun findRecent(broadcastId: Long): List<ChatResult>
}

/** SSE `chat` 이벤트와 같은 모양이다. 화면은 [id]로 목록과 SSE의 중복을 거른다. */
data class ChatResult(
    val id: Long,
    val authorNickname: String,
    val content: String,
)
