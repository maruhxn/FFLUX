package com.maruhxn.fflux.broadcast.application

/** 종료 요청으로 방송이 실제로 LIVE → ENDED가 됐다. 모듈 안에서만 쓰는 이벤트로, 커밋 뒤 송출 세션을 끊는 데 쓴다. */
data class BroadcastEndRequested(val broadcastId: Long, val mediaPath: String)
