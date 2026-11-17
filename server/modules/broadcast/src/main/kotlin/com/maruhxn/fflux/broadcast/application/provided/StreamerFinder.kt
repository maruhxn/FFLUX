package com.maruhxn.fflux.broadcast.application.provided

/** 스트리머 검색과 조회. 공개 방송(삭제되지 않은 LIVE·ENDED)이 하나라도 있는 회원을 스트리머로 본다. */
interface StreamerFinder {
    /**
     * 닉네임에 [keyword]가 든 스트리머. 방송 중인 스트리머가 먼저이고, 그 안에서는 최근에 방송한 순서다. 최대 20명.
     * 검색어가 없거나 공백뿐이면 400, 50자를 넘으면 400.
     */
    fun search(keyword: String?): List<StreamerResult>

    /** 공개 방송이 없거나 없는 회원이면 404. */
    fun find(streamerId: Long): StreamerResult
}

/** [liveBroadcastId]는 지금 방송 중인 방송의 ID이고, 방송 중이 아니면 null이다. */
data class StreamerResult(
    val streamerId: Long,
    val nickname: String,
    val liveBroadcastId: Long?,
)
