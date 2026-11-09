package com.maruhxn.fflux.recording.application

import com.maruhxn.fflux.common.error.ConflictException
import com.maruhxn.fflux.recording.application.provided.RecordingNotice
import com.maruhxn.fflux.recording.application.provided.RecordingStatusChanger
import com.maruhxn.fflux.recording.application.required.RecordedBroadcast
import com.maruhxn.fflux.recording.application.required.RecordedBroadcastFinder
import com.maruhxn.fflux.recording.application.required.RecordingRepository
import com.maruhxn.fflux.recording.domain.Recording
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.roundToLong

@Service
@Transactional
class RecordingStatusChangeService(
    private val recordingRepository: RecordingRepository,
    private val recordedBroadcastFinder: RecordedBroadcastFinder,
    private val clock: Clock,
) : RecordingStatusChanger {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun startProcessing(notice: RecordingNotice) =
        change(notice, "처리 시작", { b, now -> Recording.processing(b.id, b.streamerId, notice.segmentPath, now) }) { recording, _ ->
            recording.startProcessing(notice.segmentPath)
        }

    override fun complete(notice: RecordingNotice, filePath: String, durationSeconds: Double?) =
        change(notice, "완료", { b, now -> Recording.ready(b.id, b.streamerId, notice.segmentPath, filePath, durationOf(b, durationSeconds), now) }) { recording, b ->
            recording.complete(notice.segmentPath, filePath, durationOf(b, durationSeconds))
        }

    override fun fail(notice: RecordingNotice) =
        change(notice, "실패", { b, now -> Recording.failed(b.id, b.streamerId, notice.segmentPath, now) }) { recording, _ ->
            recording.fail(notice.segmentPath)
        }

    override fun hide(broadcastId: Long) {
        if (recordingRepository.hide(broadcastId, LocalDateTime.now(clock)) == 1) log.info("녹화 숨김: broadcastId={}", broadcastId)
    }

    /** 녹화가 아직 없으면 [create]로 만들고, 있으면 행을 잠근 뒤 [transition]에 맡긴다. */
    private fun change(
        notice: RecordingNotice,
        event: String,
        create: (RecordedBroadcast, LocalDateTime) -> Recording,
        transition: (Recording, RecordedBroadcast) -> Boolean,
    ) {
        val broadcast = recordedBroadcastFinder.findByMediaPath(notice.mediaPath)
            ?: return log.warn("녹화 {}: 방송이 없거나 삭제됨 path={}", event, notice.mediaPath)
        val recording = recordingRepository.findForUpdateByBroadcastId(broadcast.id)
        if (recording == null) {
            val created = recordingRepository.save(create(broadcast, LocalDateTime.now(clock)))
            return log.info("녹화 {}: broadcastId={} status={}", event, broadcast.id, created.status)
        }
        try {
            if (transition(recording, broadcast)) log.info("녹화 {}: broadcastId={} status={}", event, broadcast.id, recording.status)
        } catch (e: ConflictException) {
            log.warn("녹화 {}: 두 번째 세그먼트라 무시 broadcastId={} segment={}", event, broadcast.id, notice.segmentPath)
        }
    }

    /** 받은 길이가 없으면(ffprobe 없음) 방송 시작부터 종료까지의 시간으로 대신한다. 둘 중 하나라도 없으면 null. */
    private fun durationOf(broadcast: RecordedBroadcast, durationSeconds: Double?): Long? =
        durationSeconds?.roundToLong()
            ?: broadcast.startedAt?.let { start -> broadcast.endedAt?.let { end -> Duration.between(start, end).seconds } }
}
