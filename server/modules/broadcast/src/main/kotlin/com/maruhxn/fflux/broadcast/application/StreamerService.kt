package com.maruhxn.fflux.broadcast.application

import com.maruhxn.fflux.broadcast.application.provided.StreamerFinder
import com.maruhxn.fflux.broadcast.application.provided.StreamerResult
import com.maruhxn.fflux.broadcast.application.required.BroadcastRepository
import com.maruhxn.fflux.broadcast.domain.BroadcastStatus
import com.maruhxn.fflux.broadcast.domain.SearchKeyword
import com.maruhxn.fflux.common.error.BadRequestException
import com.maruhxn.fflux.common.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 스트리머 닉네임은 방송에 남긴 등록 시점 스냅샷에서 읽는다(닉네임 변경 기능은 없다). `member` 모듈을 조회하지 않는다. */
@Service
@Transactional(readOnly = true)
class StreamerService(
    private val broadcastRepository: BroadcastRepository,
) : StreamerFinder {

    override fun search(keyword: String?): List<StreamerResult> {
        val searchKeyword = SearchKeyword.of(keyword) ?: throw BadRequestException("검색어를 입력하세요.", field = "q")
        val streamers = broadcastRepository.findStreamers(searchKeyword.value, PUBLIC_STATUSES, SEARCH_LIMIT)
        val liveIds = broadcastRepository
            .findAllByStreamerIdInAndStatusAndDeletedAtIsNull(streamers.map { it.streamerId }, BroadcastStatus.LIVE)
            .associate { it.streamerId to it.id }
        return streamers
            .map { StreamerResult(it.streamerId, it.nickname, liveIds[it.streamerId]) }
    }

    override fun find(streamerId: Long): StreamerResult {
        val latest = broadcastRepository.findFirstByStreamerIdAndStatusInAndDeletedAtIsNullOrderByIdDesc(streamerId, PUBLIC_STATUSES)
            ?: throw NotFoundException("스트리머를 찾을 수 없습니다.")
        val live = broadcastRepository.findFirstByStreamerIdAndStatusAndDeletedAtIsNull(streamerId, BroadcastStatus.LIVE)
        return StreamerResult(streamerId, latest.streamerNickname, live?.id)
    }

    companion object {
        /** 비로그인에게도 보이는 방송 상태. READY는 스트리머 본인만 본다. */
        private val PUBLIC_STATUSES = listOf(BroadcastStatus.LIVE, BroadcastStatus.ENDED)
        const val SEARCH_LIMIT = 20
    }
}
