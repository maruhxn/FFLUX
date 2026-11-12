package com.maruhxn.fflux.broadcast.application.provided

import com.maruhxn.fflux.broadcast.domain.BroadcastStatus
import java.time.LocalDateTime

/** 삭제되지 않은 방송의 목록·상세 조회. 목록은 최신순이고 [cursor]는 앞 페이지의 `nextCursor`다. */
interface BroadcastFinder {
    /** 없거나 삭제됐으면 404. 녹화는 [memberId](비로그인이면 null)에게 보이는 것만 담는다. */
    fun find(broadcastId: Long, memberId: Long?): BroadcastDetailResult

    /** 송출 경로(`rec/12`)로 찾는다. 없거나 삭제됐으면 null. */
    fun findByMediaPath(mediaPath: String): BroadcastResult?

    /** 삭제되지 않은 ENDED 방송들. 없거나 삭제됐거나 끝나지 않은 방송은 빠진다. */
    fun findEnded(broadcastIds: Collection<Long>): List<BroadcastResult>

    /** 방송의 스트리머 회원 ID. 없거나 삭제됐으면 404. */
    fun findStreamerId(broadcastId: Long): Long

    /** 스트리머의 삭제되지 않은 LIVE 방송 ID. 없으면 null(LIVE는 스트리머마다 많아야 하나다). */
    fun findLiveIdOf(streamerId: Long): Long?

    /** 삭제되지 않은 LIVE 방송인지. 없거나 삭제됐으면 false. */
    fun isLive(broadcastId: Long): Boolean

    /** 메인(LIVE)·지난 방송(ENDED) 목록. READY는 공개 목록이 아니라 400. */
    fun findPublic(status: BroadcastStatus, cursor: Long?): BroadcastPage

    /** 내 방송 목록. 모든 상태를 담는다. */
    fun findMine(streamerId: Long, cursor: Long?): BroadcastPage
}

data class BroadcastResult(
    val id: Long,
    val streamerId: Long,
    val streamerNickname: String,
    val title: String,
    val description: String?,
    val status: BroadcastStatus,
    val createdAt: LocalDateTime,
    /** LIVE가 된 시각(방송 경과 시간의 기준). LIVE가 된 적 없으면 null. */
    val startedAt: LocalDateTime?,
    /** ENDED가 된 시각. 끝난 적 없으면 null. */
    val endedAt: LocalDateTime?,
    /** LIVE 방송의 지금 시청자 수. LIVE가 아니면 null. */
    val viewerCount: Int?,
)

/**
 * 상세. [watchPath]는 시청할 수 있는(LIVE) 방송의 미디어 서버 경로이고, 그 밖에는 null이다.
 * [recording]은 조회한 회원에게 보이는 녹화이고, 없거나 보이지 않으면 null이다.
 */
data class BroadcastDetailResult(
    val broadcast: BroadcastResult,
    val watchPath: String?,
    val recording: BroadcastRecordingResult?,
)

/** [status]는 `PROCESSING`, `READY`, `FAILED`. [url]은 READY 녹화의 재생 주소다. */
data class BroadcastRecordingResult(
    val status: String,
    val url: String?,
    val durationSeconds: Long?,
)

/** [nextCursor]가 null이면 마지막 페이지다. */
data class BroadcastPage(
    val items: List<BroadcastResult>,
    val nextCursor: Long?,
)
