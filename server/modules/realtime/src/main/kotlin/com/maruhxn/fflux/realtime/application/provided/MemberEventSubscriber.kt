package com.maruhxn.fflux.realtime.application.provided

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/** 로그인한 회원 자신에게 오는 SSE 구독. 돌려준 emitter는 연결이 끊기거나 제한 시간이 지나면 구독에서 빠진다. */
interface MemberEventSubscriber {
    fun subscribe(memberId: Long): SseEmitter
}
