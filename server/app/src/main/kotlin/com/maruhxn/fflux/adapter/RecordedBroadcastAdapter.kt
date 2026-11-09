package com.maruhxn.fflux.adapter

import com.maruhxn.fflux.broadcast.application.provided.BroadcastFinder
import com.maruhxn.fflux.broadcast.application.provided.BroadcastResult
import com.maruhxn.fflux.recording.application.required.RecordedBroadcast
import com.maruhxn.fflux.recording.application.required.RecordedBroadcastFinder
import org.springframework.stereotype.Component

/** recording 모듈이 녹화한 방송을 broadcast 모듈에서 찾는다. */
@Component
class RecordedBroadcastAdapter(
    private val broadcastFinder: BroadcastFinder,
) : RecordedBroadcastFinder {
    override fun findByMediaPath(mediaPath: String): RecordedBroadcast? =
        broadcastFinder.findByMediaPath(mediaPath)?.toRecorded()

    override fun findEnded(broadcastIds: Collection<Long>): List<RecordedBroadcast> =
        broadcastFinder.findEnded(broadcastIds).map { it.toRecorded() }

    private fun BroadcastResult.toRecorded() =
        RecordedBroadcast(id, streamerId, streamerNickname, title, startedAt, endedAt)
}
