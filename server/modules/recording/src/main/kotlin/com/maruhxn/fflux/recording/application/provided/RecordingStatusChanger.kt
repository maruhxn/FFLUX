package com.maruhxn.fflux.recording.application.provided

/**
 * 미디어 서버의 세그먼트 처리 통지로 녹화를 만들고 상태를 바꾼다. 통지는 반복되거나 순서가 바뀌어 와도 결과가 같다.
 * 방송을 찾을 수 없거나 두 번째 세그먼트의 통지면 바꾸지 않고 경고 로그만 남긴다(예외 없음).
 */
interface RecordingStatusChanger {
    /** 리먹싱 전: PROCESSING 녹화를 만든다. */
    fun startProcessing(notice: RecordingNotice)

    /** 리먹싱 성공: READY. [durationSeconds]가 없으면 방송 시작부터 종료까지의 시간으로 대신한다. */
    fun complete(notice: RecordingNotice, filePath: String, durationSeconds: Double?)

    /** 리먹싱 실패: FAILED. */
    fun fail(notice: RecordingNotice)

    /** 방송이 삭제되어 그 녹화를 숨긴다. 녹화가 없으면 아무것도 하지 않는다. */
    fun hide(broadcastId: Long)
}

/** [mediaPath]는 MediaMTX path(`rec/12`), [segmentPath]는 녹화한 원본 세그먼트 파일 경로다. */
data class RecordingNotice(val mediaPath: String, val segmentPath: String)
