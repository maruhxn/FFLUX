package com.maruhxn.fflux.broadcast.application

import com.maruhxn.fflux.broadcast.application.provided.IssuePublishToken
import com.maruhxn.fflux.broadcast.application.provided.IssuePublishTokenCommand
import com.maruhxn.fflux.broadcast.application.provided.PublishTokenResult
import com.maruhxn.fflux.broadcast.application.required.BroadcastRepository
import com.maruhxn.fflux.broadcast.application.required.PublishTokenRepository
import com.maruhxn.fflux.broadcast.domain.BroadcastStatus
import com.maruhxn.fflux.broadcast.domain.PublishToken
import com.maruhxn.fflux.common.error.ConflictException
import com.maruhxn.fflux.common.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional
class PublishTokenService(
    private val broadcastRepository: BroadcastRepository,
    private val publishTokenRepository: PublishTokenRepository,
    private val clock: Clock,
) : IssuePublishToken {

    override fun issue(command: IssuePublishTokenCommand): PublishTokenResult {
        val now = LocalDateTime.now(clock)
        val broadcast = broadcastRepository.findForUpdate(command.broadcastId)
            ?: throw NotFoundException("방송을 찾을 수 없습니다.")
        broadcast.preparePublish(command.memberId, command.record)
        // 송출이 시작된 뒤 LIVE가 되기 전에 경로가 바뀌면 available 훅이 방송을 찾지 못한다.
        if (publishTokenRepository.existsByBroadcastIdAndConsumedAtIsNotNull(broadcast.id)) {
            throw ConflictException("이미 송출을 시작한 방송입니다. 송출이 연결되지 않았다면 방송을 새로 등록하세요.")
        }
        if (broadcastRepository.existsByStreamerIdAndStatusAndDeletedAtIsNull(broadcast.streamerId, BroadcastStatus.LIVE)) {
            throw ConflictException("이미 송출 중인 다른 방송이 있습니다.")
        }
        publishTokenRepository.revokeUnconsumed(broadcast.id, now)
        val issued = PublishToken.issue(broadcast.id, now)
        publishTokenRepository.save(issued.token)
        return PublishTokenResult(token = issued.raw, mediaPath = checkNotNull(broadcast.mediaPath))
    }
}
