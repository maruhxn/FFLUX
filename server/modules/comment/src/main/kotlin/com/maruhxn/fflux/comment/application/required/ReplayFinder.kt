package com.maruhxn.fflux.comment.application.required

/** 댓글을 달 방송이 다시보기인지 본다(구현은 `app` 어댑터). */
interface ReplayFinder {
    /** 방송이 끝났고(ENDED) 재생할 수 있는(READY) 녹화가 있는지. 없거나 삭제된 방송이면 404(`NotFoundException`)를 던진다. */
    fun isReplay(broadcastId: Long): Boolean
}
