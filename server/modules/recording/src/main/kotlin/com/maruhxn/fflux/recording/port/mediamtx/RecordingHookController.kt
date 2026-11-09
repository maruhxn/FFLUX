package com.maruhxn.fflux.recording.port.mediamtx

import com.maruhxn.fflux.recording.application.provided.RecordingNotice
import com.maruhxn.fflux.recording.application.provided.RecordingStatusChanger
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 녹화 세그먼트 처리 훅(`infra/hooks/remux-recording.sh`)의 통지. `/internal`은 Caddy가 라우팅하지 않아 Docker 내부망에서만 닿는다.
 * 처리할 수 없는 통지도 200으로 응답한다(판단과 로그는 [RecordingStatusChanger]가 한다).
 */
@RestController
@RequestMapping("/internal/mediamtx/recordings")
class RecordingHookController(
    private val recordingStatusChanger: RecordingStatusChanger,
) {
    /** 리먹싱 전. */
    @PostMapping("/processing")
    fun processing(@RequestBody request: RecordingHookRequest): ResponseEntity<Unit> {
        recordingStatusChanger.startProcessing(request.notice())
        return ResponseEntity.ok().build()
    }

    /** 리먹싱 성공. */
    @PostMapping("/ready")
    fun ready(@RequestBody request: RecordingReadyRequest): ResponseEntity<Unit> {
        recordingStatusChanger.complete(RecordingNotice(request.path, request.segmentPath), request.filePath, request.durationSeconds)
        return ResponseEntity.ok().build()
    }

    /** 리먹싱 실패. */
    @PostMapping("/failed")
    fun failed(@RequestBody request: RecordingHookRequest): ResponseEntity<Unit> {
        recordingStatusChanger.fail(request.notice())
        return ResponseEntity.ok().build()
    }
}

/** [path]는 MediaMTX path(`rec/12`), [segmentPath]는 원본 세그먼트 파일 경로(`MTX_SEGMENT_PATH`)다. */
data class RecordingHookRequest(val path: String, val segmentPath: String) {
    fun notice() = RecordingNotice(path, segmentPath)
}

/** [filePath]는 리먹싱한 MP4의 녹화 디렉터리 기준 경로, [durationSeconds]는 ffprobe 길이(없으면 생략)다. */
data class RecordingReadyRequest(
    val path: String,
    val segmentPath: String,
    val filePath: String,
    val durationSeconds: Double? = null,
)
