package com.maruhxn.fflux.chat.port.web

import com.maruhxn.fflux.chat.application.provided.ChatFinder
import com.maruhxn.fflux.chat.application.provided.ChatWriter
import com.maruhxn.fflux.chat.application.provided.RecentChatsResult
import com.maruhxn.fflux.chat.application.provided.WriteChatCommand
import com.maruhxn.fflux.common.auth.LoginMember
import com.maruhxn.fflux.common.auth.PublicApi
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 채팅은 방송 아래에서 쓰고(로그인), 입장할 때 최근 채팅을 읽는다(비로그인 허용). 그 뒤는 방송별 SSE `chat`. */
@RestController
@RequestMapping("/api/broadcasts/{broadcastId}/chats")
class ChatController(
    private val chatWriter: ChatWriter,
    private val chatFinder: ChatFinder,
) {
    @PublicApi
    @GetMapping
    fun recent(member: LoginMember?, @PathVariable broadcastId: Long): RecentChatsResult = chatFinder.findRecent(broadcastId, member?.memberId)

    @PostMapping
    fun write(member: LoginMember, @PathVariable broadcastId: Long, @RequestBody request: ChatRequest): ResponseEntity<WriteChatResponse> {
        val id = chatWriter.write(WriteChatCommand(broadcastId, member.memberId, member.nickname, request.content))
        return ResponseEntity.status(HttpStatus.CREATED).body(WriteChatResponse(id))
    }
}

/** 본문 규칙(공백 제거, 길이)은 도메인 값 객체가 검사한다. */
data class ChatRequest(val content: String)

data class WriteChatResponse(val id: Long)
