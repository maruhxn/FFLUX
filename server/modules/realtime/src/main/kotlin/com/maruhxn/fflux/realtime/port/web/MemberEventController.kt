package com.maruhxn.fflux.realtime.port.web

import com.maruhxn.fflux.common.auth.LoginMember
import com.maruhxn.fflux.realtime.application.provided.MemberEventSubscriber
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
class MemberEventController(
    private val memberEventSubscriber: MemberEventSubscriber,
) {
    /** 로그인한 회원 자신의 SSE. 이벤트: `broadcast-started`(`{broadcastId, streamerNickname, title}`, 팔로우한 스트리머의 방송이 LIVE가 됨). */
    @GetMapping("/api/me/events")
    fun subscribe(member: LoginMember): SseEmitter = memberEventSubscriber.subscribe(member.memberId)
}
