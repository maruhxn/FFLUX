package com.maruhxn.fflux.chat.port.web

import com.maruhxn.fflux.chat.application.provided.ChatModerator
import com.maruhxn.fflux.chat.application.provided.ChatRestrictionResult
import com.maruhxn.fflux.chat.domain.ChatRestrictionDuration
import com.maruhxn.fflux.common.auth.LoginMember
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 스트리머의 채팅 관리. 모두 로그인이 필요하고, 스트리머인지는 엔티티가 확인한다. */
@RestController
@RequestMapping("/api")
class ChatModerationController(
    private val chatModerator: ChatModerator,
) {
    @PostMapping("/chats/{id}/hide")
    fun hide(member: LoginMember, @PathVariable id: Long): ResponseEntity<Unit> {
        chatModerator.hide(id, member.memberId)
        return ResponseEntity.noContent().build()
    }

    /** 이 채팅을 쓴 회원을 채팅 금지한다. */
    @PostMapping("/chats/{id}/restrict")
    fun restrict(member: LoginMember, @PathVariable id: Long, @RequestBody request: ChatRestrictRequest): ResponseEntity<Unit> {
        chatModerator.restrict(id, member.memberId, request.duration)
        return ResponseEntity.noContent().build()
    }

    /** 로그인한 스트리머 자신의 채팅 금지를 푼다. */
    @DeleteMapping("/chat-restrictions/{memberId}")
    fun lift(member: LoginMember, @PathVariable memberId: Long): ResponseEntity<Unit> {
        chatModerator.lift(member.memberId, memberId)
        return ResponseEntity.noContent().build()
    }

    /** 로그인한 스트리머 자신의 채팅 금지 목록. */
    @GetMapping("/chat-restrictions")
    fun restrictions(member: LoginMember): List<ChatRestrictionResult> = chatModerator.findRestrictions(member.memberId)
}

data class ChatRestrictRequest(val duration: ChatRestrictionDuration)
