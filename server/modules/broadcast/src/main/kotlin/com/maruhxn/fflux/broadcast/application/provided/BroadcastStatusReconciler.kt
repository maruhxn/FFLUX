package com.maruhxn.fflux.broadcast.application.provided

/**
 * 상태 맞추기(docs/decisions.md 3절). MediaMTX 훅은 한 번만 오므로, 놓친 훅을 미디어 서버의 지금 송출 경로로 바로잡는다.
 * 전이는 [BroadcastStatusChanger]에 맡긴다.
 */
interface BroadcastStatusReconciler {
    /** 송출 없는 LIVE는 송출 끊김, 송출 들어오는 READY는 송출 시작 확인, 끝났거나 삭제된 방송에 남은 송출은 끊는다. */
    fun reconcile()
}
