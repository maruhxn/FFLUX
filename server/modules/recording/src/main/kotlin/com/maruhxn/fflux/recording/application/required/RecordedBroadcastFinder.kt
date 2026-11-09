package com.maruhxn.fflux.recording.application.required

import java.time.LocalDateTime

/** 녹화한 방송을 찾는다(구현은 `app` 어댑터). 삭제된 방송은 없는 것으로 본다. */
interface RecordedBroadcastFinder {
    /** 송출 경로(`rec/12`)로 찾는다. 없으면 null. */
    fun findByMediaPath(mediaPath: String): RecordedBroadcast?

    /** 끝난(ENDED) 방송들. 없거나 끝나지 않은 방송은 빠진다. */
    fun findEnded(broadcastIds: Collection<Long>): List<RecordedBroadcast>
}

data class RecordedBroadcast(
    val id: Long,
    val streamerId: Long,
    val streamerNickname: String,
    val title: String,
    val startedAt: LocalDateTime?,
    val endedAt: LocalDateTime?,
)
