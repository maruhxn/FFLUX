package com.maruhxn.fflux.broadcast.application

import com.maruhxn.fflux.broadcast.application.provided.BroadcastDetailResult
import com.maruhxn.fflux.broadcast.application.provided.BroadcastPage
import com.maruhxn.fflux.broadcast.application.provided.BroadcastResult
import com.maruhxn.fflux.broadcast.application.provided.EditBroadcastCommand
import com.maruhxn.fflux.broadcast.application.provided.BroadcastFinder
import com.maruhxn.fflux.broadcast.application.provided.BroadcastModifier
import com.maruhxn.fflux.broadcast.application.provided.RegisterBroadcastCommand
import com.maruhxn.fflux.broadcast.application.required.BroadcastRepository
import com.maruhxn.fflux.broadcast.application.required.PublishTokenRepository
import com.maruhxn.fflux.broadcast.application.required.ViewerCounter
import com.maruhxn.fflux.broadcast.domain.Broadcast
import com.maruhxn.fflux.broadcast.domain.BroadcastDescription
import com.maruhxn.fflux.broadcast.domain.BroadcastStatus
import com.maruhxn.fflux.broadcast.domain.BroadcastTitle
import com.maruhxn.fflux.common.error.BadRequestException
import com.maruhxn.fflux.common.error.NotFoundException
import com.maruhxn.fflux.common.event.BroadcastUpdated
import com.maruhxn.fflux.common.event.EventPublisherPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional
class BroadcastService(
    private val broadcastRepository: BroadcastRepository,
    private val publishTokenRepository: PublishTokenRepository,
    private val eventPublisher: EventPublisherPort,
    private val viewerCounter: ViewerCounter,
    private val clock: Clock,
) : BroadcastModifier, BroadcastFinder {

    override fun register(command: RegisterBroadcastCommand): Long {
        val (title, description) = titleAndDescription(command.title, command.description)
        val broadcast = Broadcast.register(
            streamerId = command.streamerId,
            streamerNickname = command.streamerNickname,
            title = title,
            description = description,
            now = LocalDateTime.now(clock),
        )
        return broadcastRepository.save(broadcast).id
    }

    override fun edit(command: EditBroadcastCommand) {
        // 입력 검증(400)을 대상 조회(404)보다 먼저 한다.
        val (title, description) = titleAndDescription(command.title, command.description)
        val broadcast = findForUpdateOrThrow(command.broadcastId)
        broadcast.edit(command.memberId, title, description)
        eventPublisher.publish(BroadcastUpdated(broadcast.id, broadcast.title.value, broadcast.description?.value))
    }

    override fun delete(broadcastId: Long, memberId: Long) {
        val now = LocalDateTime.now(clock)
        findForUpdateOrThrow(broadcastId).delete(memberId, now)
        publishTokenRepository.revokeUnconsumed(broadcastId, now)
    }

    @Transactional(readOnly = true)
    override fun find(broadcastId: Long): BroadcastDetailResult {
        val broadcast = broadcastRepository.findByIdAndDeletedAtIsNull(broadcastId) ?: throw notFound()
        val viewerCount = viewerCountsOf(listOf(broadcast))[broadcast.id]
        return BroadcastDetailResult(
            broadcast.toResult(viewerCount),
            watchPath = broadcast.mediaPath.takeIf { broadcast.isWatchable() },
        )
    }

    @Transactional(readOnly = true)
    override fun findStreamerId(broadcastId: Long): Long =
        (broadcastRepository.findByIdAndDeletedAtIsNull(broadcastId) ?: throw notFound()).streamerId

    @Transactional(readOnly = true)
    override fun findPublic(status: BroadcastStatus, cursor: Long?): BroadcastPage {
        if (status == BroadcastStatus.READY) throw BadRequestException("공개 목록은 LIVE 또는 ENDED만 조회할 수 있습니다.")
        return page(status = status, streamerId = null, cursor = cursor)
    }

    @Transactional(readOnly = true)
    override fun findMine(streamerId: Long, cursor: Long?): BroadcastPage =
        page(status = null, streamerId = streamerId, cursor = cursor)

    /** 상태 전이와 순서가 엇갈리지 않도록 행을 잠근다. 스트리머 확인과 상태 조건은 엔티티가 판단한다. */
    private fun findForUpdateOrThrow(broadcastId: Long): Broadcast =
        broadcastRepository.findForUpdate(broadcastId) ?: throw notFound()

    /** 한 개를 더 읽어 다음 페이지가 있는지 판단한다. */
    private fun page(status: BroadcastStatus?, streamerId: Long?, cursor: Long?): BroadcastPage {
        val rows = broadcastRepository.findPage(status, streamerId, cursor, PAGE_SIZE + 1)
        val items = rows.take(PAGE_SIZE)
        val viewerCounts = viewerCountsOf(items)
        return BroadcastPage(
            items = items.map { it.toResult(viewerCounts[it.id]) },
            nextCursor = if (rows.size > PAGE_SIZE) items.last().id else null,
        )
    }

    /** 두 칸이 모두 틀리면 두 칸의 에러를 한 번에 알린다. */
    private fun titleAndDescription(title: String, description: String?): Pair<BroadcastTitle, BroadcastDescription?> {
        val titleResult = runCatching { BroadcastTitle.of(title) }
        val descriptionResult = runCatching { BroadcastDescription.of(description) }
        BadRequestException.throwIfAny(listOf(titleResult, descriptionResult))
        return titleResult.getOrThrow() to descriptionResult.getOrThrow()
    }

    private fun notFound() = NotFoundException("방송을 찾을 수 없습니다.")

    /** 시청자 수는 LIVE 방송에만 있다. */
    private fun viewerCountsOf(broadcasts: List<Broadcast>): Map<Long, Int> =
        broadcasts.filter { it.isWatchable() }.map { it.id }
            .takeIf { it.isNotEmpty() }?.let(viewerCounter::viewerCounts).orEmpty()

    private fun Broadcast.toResult(viewerCount: Int?) = BroadcastResult(
        id = id,
        streamerId = streamerId,
        streamerNickname = streamerNickname,
        title = title.value,
        description = description?.value,
        status = status,
        createdAt = createdAt,
        startedAt = startedAt,
        viewerCount = viewerCount,
    )

    companion object {
        const val PAGE_SIZE = 20
    }
}
