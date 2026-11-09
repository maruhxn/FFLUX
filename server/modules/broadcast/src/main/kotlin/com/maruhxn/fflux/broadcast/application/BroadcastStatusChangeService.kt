package com.maruhxn.fflux.broadcast.application

import com.maruhxn.fflux.broadcast.application.provided.BroadcastStatusChanger
import com.maruhxn.fflux.broadcast.application.required.BroadcastRepository
import com.maruhxn.fflux.broadcast.application.required.PublishTokenRepository
import com.maruhxn.fflux.broadcast.domain.Broadcast
import com.maruhxn.fflux.broadcast.domain.BroadcastStatus
import com.maruhxn.fflux.broadcast.domain.PublishToken
import com.maruhxn.fflux.common.error.ConflictException
import com.maruhxn.fflux.common.error.NotFoundException
import com.maruhxn.fflux.common.event.BroadcastStatusChanged
import com.maruhxn.fflux.common.event.EventPublisherPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional
class BroadcastStatusChangeService(
    private val broadcastRepository: BroadcastRepository,
    private val publishTokenRepository: PublishTokenRepository,
    private val eventPublisher: EventPublisherPort,
    private val clock: Clock,
) : BroadcastStatusChanger {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun authorizePublish(mediaPath: String, token: String?): Boolean {
        if (token.isNullOrEmpty()) return deny(mediaPath, "송출 토큰 없음")
        val publishToken = publishTokenRepository.findByTokenHash(PublishToken.hash(token))
            ?: return deny(mediaPath, "모르는 송출 토큰")
        val broadcast = broadcastRepository.findByIdAndDeletedAtIsNull(publishToken.broadcastId)
            ?: return deny(mediaPath, "방송이 없거나 삭제됨")
        if (!broadcast.canStartPublishAt(mediaPath)) return deny(mediaPath, "경로 불일치 또는 READY 아님")
        if (broadcastRepository.existsByStreamerIdAndStatusAndDeletedAtIsNull(broadcast.streamerId, BroadcastStatus.LIVE)) {
            return deny(mediaPath, "스트리머의 다른 LIVE 방송 있음")
        }
        // 미만료·미소비·미무효화를 UPDATE 조건으로 확인해 동시에 와도 한 번만 소비한다.
        if (publishTokenRepository.consume(publishToken.id, LocalDateTime.now(clock)) != 1) {
            return deny(mediaPath, "만료·소비·무효화된 송출 토큰")
        }
        log.info("송출 허가: path={}", mediaPath)
        return true
    }

    @Transactional(readOnly = true)
    override fun authorizeRead(mediaPath: String): Boolean =
        broadcastRepository.findByMediaPathAndDeletedAtIsNull(mediaPath)?.isWatchable() ?: false

    override fun confirmPublishStarted(mediaPath: String) {
        val broadcast = broadcastRepository.findForUpdateByMediaPath(mediaPath)
            ?: return log.warn("송출 시작 확인: 방송이 없거나 삭제됨 path={}", mediaPath)
        try {
            if (broadcast.confirmPublishStarted(LocalDateTime.now(clock))) {
                log.info("방송 LIVE 전환: id={}", broadcast.id)
                publishStatusChanged(broadcast)
            }
        } catch (e: ConflictException) {
            log.warn("송출 시작 확인: 전이할 수 없음 id={} status={}", broadcast.id, broadcast.status)
        }
    }

    override fun confirmPublishEnded(mediaPath: String) {
        val broadcast = broadcastRepository.findForUpdateByMediaPath(mediaPath)
            ?: return log.warn("송출 끊김: 방송이 없거나 삭제됨 path={}", mediaPath)
        try {
            if (broadcast.endByDisconnect(LocalDateTime.now(clock))) {
                log.info("방송 종료(송출 끊김): id={}", broadcast.id)
                publishStatusChanged(broadcast)
            }
        } catch (e: ConflictException) {
            log.warn("송출 끊김: 전이할 수 없음 id={} status={}", broadcast.id, broadcast.status)
        }
    }

    override fun requestEnd(broadcastId: Long, memberId: Long) {
        val broadcast = broadcastRepository.findForUpdate(broadcastId) ?: throw NotFoundException("방송을 찾을 수 없습니다.")
        if (!broadcast.requestEnd(memberId, LocalDateTime.now(clock))) return
        log.info("방송 종료(종료 요청): id={}", broadcast.id)
        publishStatusChanged(broadcast)
        eventPublisher.publish(BroadcastEndRequested(broadcast.id, checkNotNull(broadcast.mediaPath)))
    }

    /** 전이가 실제로 일어났을 때만 부른다. 받는 쪽은 커밋 뒤에 받는다. */
    private fun publishStatusChanged(broadcast: Broadcast) {
        eventPublisher.publish(BroadcastStatusChanged(broadcast.id, broadcast.status.name))
    }

    private fun deny(mediaPath: String, reason: String): Boolean {
        log.warn("송출 거절: path={} 이유={}", mediaPath, reason)
        return false
    }
}
