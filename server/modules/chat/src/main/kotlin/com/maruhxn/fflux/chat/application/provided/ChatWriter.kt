package com.maruhxn.fflux.chat.application.provided

/** 채팅 작성. 고치거나 지우는 기능은 없다. */
interface ChatWriter {
    /** LIVE 방송에만 쓴다. 없거나 삭제된 방송은 404, LIVE가 아니면 409. 만든 채팅 ID를 돌려준다. */
    fun write(command: WriteChatCommand): Long
}

/** [authorNickname]은 작성 시점의 닉네임으로 남긴다. */
data class WriteChatCommand(
    val broadcastId: Long,
    val authorId: Long,
    val authorNickname: String,
    val content: String,
)
