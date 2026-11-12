package com.maruhxn.fflux.broadcast.application.provided

import java.time.LocalDateTime

/**
 * 방송 상태 전이 module(docs/decisions.md 3절). 미디어 서버의 인증 콜백과 훅은 이 module을 부르기만 한다.
 * [mediaPath]로 방송을 찾는 일도 여기서 한다. 미디어 서버가 부르는 메서드는 예외로 실패하지 않는다.
 */
interface BroadcastStatusChanger {
    /** 송출 허가: 토큰이 유효하고 방송이 송출을 시작할 수 있으면 토큰을 1회 소비하고 true. */
    fun authorizePublish(mediaPath: String, token: String?): Boolean

    /** 시청 허가: 방송이 LIVE이고 삭제되지 않았으면 true. */
    fun authorizeRead(mediaPath: String): Boolean

    /**
     * 송출 시작 확인: READY → LIVE. 이미 LIVE면 그대로 두고, 전이할 수 없으면 경고 로그만 남긴다.
     * 시작 시각은 [startedAt]이고, 없으면(훅) 지금이다. 상태 맞추기는 미디어 서버가 송출을 받기 시작한 시각을 넘긴다.
     */
    fun confirmPublishStarted(mediaPath: String, startedAt: LocalDateTime? = null)

    /** 송출 끊김: LIVE → ENDED. 이미 ENDED면 그대로 두고, 전이할 수 없으면 경고 로그만 남긴다. */
    fun confirmPublishEnded(mediaPath: String)

    /**
     * 종료 요청: 스트리머가 LIVE 방송을 끝내고, 커밋 뒤 송출 세션을 끊는다. 이미 ENDED면 성공한다.
     * 이 메서드만 예외로 실패한다: 없거나 삭제됐으면 404, 스트리머가 아니면 403, READY면 409.
     */
    fun requestEnd(broadcastId: Long, memberId: Long)
}
