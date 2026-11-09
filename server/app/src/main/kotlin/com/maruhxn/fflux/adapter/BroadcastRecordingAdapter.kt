package com.maruhxn.fflux.adapter

import com.maruhxn.fflux.broadcast.application.provided.BroadcastRecordingResult
import com.maruhxn.fflux.broadcast.application.required.BroadcastRecordingFinder
import com.maruhxn.fflux.recording.application.provided.RecordingFinder
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * 방송 상세의 녹화를 recording 모듈에 맡긴다.
 * recording 모듈도 녹화한 방송을 broadcast 모듈로 찾으므로, 빈 생성 순환을 [Lazy]로 끊는다.
 */
@Component
class BroadcastRecordingAdapter(
    @Lazy private val recordingFinder: RecordingFinder,
) : BroadcastRecordingFinder {
    override fun recordingOf(broadcastId: Long, memberId: Long?): BroadcastRecordingResult? =
        recordingFinder.findVisible(broadcastId, memberId)
            ?.let { BroadcastRecordingResult(it.status, it.url, it.durationSeconds) }
}
