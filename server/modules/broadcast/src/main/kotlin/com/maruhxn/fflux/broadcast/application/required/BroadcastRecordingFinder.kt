package com.maruhxn.fflux.broadcast.application.required

import com.maruhxn.fflux.broadcast.application.provided.BroadcastRecordingResult

/** 방송 상세에 담을 녹화를 찾는다(구현은 `app` 어댑터). */
interface BroadcastRecordingFinder {
    /** [memberId](비로그인이면 null)에게 보이는 방송의 녹화. 없거나 보이지 않으면 null. */
    fun recordingOf(broadcastId: Long, memberId: Long?): BroadcastRecordingResult?
}
