package com.maruhxn.fflux.recording.application.provided

import java.time.LocalDateTime

interface RecordingFinder {
    /** [memberId]에게 보이는 방송의 녹화. READY는 모두에게, 처리 중·실패는 스트리머에게만 보인다. 보이지 않으면 null. */
    fun findVisible(broadcastId: Long, memberId: Long?): RecordingResult?

    /** 다시보기 목록: 삭제되지 않은 끝난 방송의 READY 녹화, 방송 최신순. [cursor]는 앞 페이지의 `nextCursor`다. */
    fun findReplays(cursor: Long?): ReplayPage
}

/** [status]는 `PROCESSING`, `READY`, `FAILED`. [url]은 READY 녹화의 재생 주소(같은 도메인의 `/vod/...`)이고, 그 밖에는 null이다. */
data class RecordingResult(
    val status: String,
    val url: String?,
    val durationSeconds: Long?,
)

data class ReplayResult(
    val broadcastId: Long,
    val title: String,
    val streamerNickname: String,
    val durationSeconds: Long?,
    /** 방송이 LIVE가 된 시각(방송 날짜). */
    val startedAt: LocalDateTime?,
)

/** [nextCursor]가 null이면 마지막 페이지다. */
data class ReplayPage(
    val items: List<ReplayResult>,
    val nextCursor: Long?,
)
