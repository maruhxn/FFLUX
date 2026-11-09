package com.maruhxn.fflux.recording.application

import com.maruhxn.fflux.recording.application.provided.RecordingFinder
import com.maruhxn.fflux.recording.application.provided.RecordingResult
import com.maruhxn.fflux.recording.application.provided.ReplayPage
import com.maruhxn.fflux.recording.application.provided.ReplayResult
import com.maruhxn.fflux.recording.application.required.RecordedBroadcastFinder
import com.maruhxn.fflux.recording.application.required.RecordingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RecordingService(
    private val recordingRepository: RecordingRepository,
    private val recordedBroadcastFinder: RecordedBroadcastFinder,
) : RecordingFinder {

    override fun findVisible(broadcastId: Long, memberId: Long?): RecordingResult? =
        recordingRepository.findByBroadcastId(broadcastId)
            ?.takeIf { it.isVisibleTo(memberId) }
            ?.let { RecordingResult(it.status.name, it.filePath?.let(::playbackUrl), it.durationSeconds) }

    /** 한 개를 더 읽어 다음 페이지가 있는지 판단한다. 아직 끝나지 않은 방송(드문 순서 뒤바뀜)은 이 페이지에서 뺀다. */
    override fun findReplays(cursor: Long?): ReplayPage {
        val rows = recordingRepository.findReplayPage(cursor, PAGE_SIZE + 1)
        val recordings = rows.take(PAGE_SIZE)
        val broadcasts = recordedBroadcastFinder.findEnded(recordings.map { it.broadcastId }).associateBy { it.id }
        return ReplayPage(
            items = recordings.mapNotNull { recording ->
                broadcasts[recording.broadcastId]?.let {
                    ReplayResult(it.id, it.title, it.streamerNickname, recording.durationSeconds, it.startedAt)
                }
            },
            nextCursor = if (rows.size > PAGE_SIZE) recordings.last().broadcastId else null,
        )
    }

    /** Caddy가 녹화 디렉터리를 같은 도메인의 `/vod/` 아래로 정적 서빙한다. */
    private fun playbackUrl(filePath: String) = "/vod/$filePath"

    companion object {
        const val PAGE_SIZE = 20
    }
}
