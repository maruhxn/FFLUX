package com.maruhxn.fflux.broadcast.application

import com.maruhxn.fflux.broadcast.application.provided.BroadcastStatusChanger
import com.maruhxn.fflux.broadcast.application.provided.BroadcastStatusReconciler
import com.maruhxn.fflux.broadcast.application.required.BroadcastRepository
import com.maruhxn.fflux.broadcast.application.required.MediaPublisherKicker
import com.maruhxn.fflux.broadcast.application.required.MediaPublishingFinder
import com.maruhxn.fflux.broadcast.domain.BroadcastStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 트랜잭션을 열지 않는다. 전이는 방송마다 [BroadcastStatusChanger]가 행을 잠근 자기 트랜잭션에서 하고, kick은 트랜잭션 밖에서 한다.
 * DB의 LIVE 방송을 먼저 읽고 미디어 서버 목록을 나중에 읽는다. LIVE는 송출이 들어온 뒤에만 되므로, 그사이 LIVE가 된 방송을 잘못 끝내지 않는다.
 */
@Service
class BroadcastStatusReconcileService(
    private val broadcastRepository: BroadcastRepository,
    private val mediaPublishingFinder: MediaPublishingFinder,
    private val mediaPublisherKicker: MediaPublisherKicker,
    private val broadcastStatusChanger: BroadcastStatusChanger,
) : BroadcastStatusReconciler {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun reconcile() {
        val livePaths = broadcastRepository.findAllByStatusAndDeletedAtIsNull(BroadcastStatus.LIVE).mapNotNull { it.mediaPath }
        val publishings = try {
            mediaPublishingFinder.findPublishings()
        } catch (e: Exception) {
            return log.warn("상태 맞추기: 미디어 서버 조회 실패, 이번에는 건너뜀", e)
        }

        val publishingPaths = publishings.map { it.mediaPath }.toSet()
        livePaths.filterNot { it in publishingPaths }.forEach { path ->
            log.info("상태 맞추기: 송출 없는 LIVE 방송 path={}", path)
            broadcastStatusChanger.confirmPublishEnded(path)
        }

        publishings.forEach { publishing ->
            val broadcast = broadcastRepository.findByMediaPathAndDeletedAtIsNull(publishing.mediaPath)
            when (broadcast?.status) {
                BroadcastStatus.READY -> {
                    log.info("상태 맞추기: 송출 들어오는 READY 방송 path={}", publishing.mediaPath)
                    broadcastStatusChanger.confirmPublishStarted(publishing.mediaPath, publishing.availableAt)
                }
                BroadcastStatus.ENDED, null -> kick(publishing.mediaPath)
                BroadcastStatus.LIVE -> Unit
            }
        }
    }

    private fun kick(mediaPath: String) {
        log.info("상태 맞추기: 끝났거나 삭제된 방송에 남은 송출 path={}", mediaPath)
        try {
            mediaPublisherKicker.kickPublisher(mediaPath)
        } catch (e: Exception) {
            log.warn("상태 맞추기: 송출 세션 kick 실패 path={}", mediaPath, e)
        }
    }
}
