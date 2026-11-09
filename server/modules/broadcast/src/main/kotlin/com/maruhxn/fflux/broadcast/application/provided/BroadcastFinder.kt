package com.maruhxn.fflux.broadcast.application.provided

import com.maruhxn.fflux.broadcast.domain.BroadcastStatus
import java.time.LocalDateTime

/** 삭제되지 않은 방송의 목록·상세 조회. 목록은 최신순이고 [cursor]는 앞 페이지의 `nextCursor`다. */
interface BroadcastFinder {
    /** 없거나 삭제됐으면 404. */
    fun find(broadcastId: Long): BroadcastDetailResult

    /** 방송의 스트리머 회원 ID. 없거나 삭제됐으면 404. */
    fun findStreamerId(broadcastId: Long): Long

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
    /** LIVE 방송의 지금 시청자 수. LIVE가 아니면 null. */
    val viewerCount: Int?,
)

/** 상세. [watchPath]는 시청할 수 있는(LIVE) 방송의 미디어 서버 경로이고, 그 밖에는 null이다. */
data class BroadcastDetailResult(
    val broadcast: BroadcastResult,
    val watchPath: String?,
)

/** [nextCursor]가 null이면 마지막 페이지다. */
data class BroadcastPage(
    val items: List<BroadcastResult>,
    val nextCursor: Long?,
)
