package com.maruhxn.fflux.realtime.application.provided

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/** 방송별 SSE 구독. */
interface BroadcastEventSubscriber {
    /**
     * 없거나 삭제된 방송이면 404. 돌려준 emitter는 연결이 끊기거나 제한 시간이 지나면 구독에서 빠진다.
     * [memberId]는 로그인한 회원이면 그 ID, 비로그인이면 null이다. 로그인한 회원만 시청자 수에 들어간다.
     */
    fun subscribe(broadcastId: Long, memberId: Long?): SseEmitter
}
