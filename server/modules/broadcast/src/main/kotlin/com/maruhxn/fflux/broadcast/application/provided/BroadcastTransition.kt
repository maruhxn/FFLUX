package com.maruhxn.fflux.broadcast.application.provided

/**
 * 방송 상태 전이 module(docs/decisions.md 3절). 미디어 서버의 인증 콜백과 훅은 이 module을 부르기만 한다.
 * [mediaPath]로 방송을 찾는 일도 여기서 한다. 어느 메서드도 예외로 실패하지 않는다.
 */
interface BroadcastTransition {
    /** 송출 허가: 토큰이 유효하고 방송이 송출을 시작할 수 있으면 토큰을 1회 소비하고 true. */
    fun authorizePublish(mediaPath: String, token: String?): Boolean

    /** 시청 허가: 방송이 LIVE이고 삭제되지 않았으면 true. */
    fun authorizeRead(mediaPath: String): Boolean

    /** 송출 시작 확인: READY → LIVE. 이미 LIVE면 그대로 두고, 전이할 수 없으면 경고 로그만 남긴다. */
    fun confirmPublishStarted(mediaPath: String)
}
