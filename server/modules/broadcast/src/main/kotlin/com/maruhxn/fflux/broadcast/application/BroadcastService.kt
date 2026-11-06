package com.maruhxn.fflux.broadcast.application

import com.maruhxn.fflux.broadcast.application.provided.BroadcastPage
import com.maruhxn.fflux.broadcast.application.provided.BroadcastResult
import com.maruhxn.fflux.broadcast.application.provided.EditBroadcastCommand
import com.maruhxn.fflux.broadcast.application.provided.FindBroadcast
import com.maruhxn.fflux.broadcast.application.provided.ManageBroadcast
import com.maruhxn.fflux.broadcast.application.provided.RegisterBroadcastCommand
import com.maruhxn.fflux.broadcast.application.required.BroadcastRepository
import com.maruhxn.fflux.broadcast.domain.Broadcast
import com.maruhxn.fflux.broadcast.domain.BroadcastStatus
import com.maruhxn.fflux.common.error.BadRequestException
import com.maruhxn.fflux.common.error.ForbiddenException
import com.maruhxn.fflux.common.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional
class BroadcastService(
    private val broadcastRepository: BroadcastRepository,
    private val clock: Clock,
) : ManageBroadcast, FindBroadcast {

    override fun register(command: RegisterBroadcastCommand): Long {
        val broadcast = Broadcast(
            streamerId = command.streamerId,
            streamerNickname = command.streamerNickname,
            title = normalizeTitle(command.title),
            description = normalizeDescription(command.description),
            createdAt = LocalDateTime.now(clock),
        )
        return broadcastRepository.save(broadcast).id
    }

    override fun edit(command: EditBroadcastCommand) {
        val broadcast = lockOwnedBy(command.broadcastId, command.memberId)
        broadcast.edit(normalizeTitle(command.title), normalizeDescription(command.description))
    }

    override fun delete(broadcastId: Long, memberId: Long) {
        lockOwnedBy(broadcastId, memberId).delete(LocalDateTime.now(clock))
    }

    @Transactional(readOnly = true)
    override fun find(broadcastId: Long): BroadcastResult =
        (broadcastRepository.findByIdAndDeletedAtIsNull(broadcastId) ?: throw notFound()).toResult()

    @Transactional(readOnly = true)
    override fun findPublic(status: BroadcastStatus, cursor: Long?): BroadcastPage {
        if (status == BroadcastStatus.READY) throw BadRequestException("공개 목록은 LIVE 또는 ENDED만 조회할 수 있습니다.")
        return page(status = status, streamerId = null, cursor = cursor)
    }

    @Transactional(readOnly = true)
    override fun findMine(streamerId: Long, cursor: Long?): BroadcastPage =
        page(status = null, streamerId = streamerId, cursor = cursor)

    /** 상태 전이와 순서가 엇갈리지 않도록 행을 잠근 뒤 스트리머를 확인한다. */
    private fun lockOwnedBy(broadcastId: Long, memberId: Long): Broadcast {
        val broadcast = broadcastRepository.findForUpdate(broadcastId) ?: throw notFound()
        if (!broadcast.isStreamer(memberId)) throw ForbiddenException("방송의 스트리머만 할 수 있습니다.")
        return broadcast
    }

    /** 한 개를 더 읽어 다음 페이지가 있는지 판단한다. */
    private fun page(status: BroadcastStatus?, streamerId: Long?, cursor: Long?): BroadcastPage {
        val rows = broadcastRepository.findPage(status, streamerId, cursor, PAGE_SIZE + 1)
        val items = rows.take(PAGE_SIZE)
        return BroadcastPage(
            items = items.map { it.toResult() },
            nextCursor = if (rows.size > PAGE_SIZE) items.last().id else null,
        )
    }

    private fun normalizeTitle(title: String) = title.trim()

    /** 공백뿐인 설명은 없음으로 저장한다. */
    private fun normalizeDescription(description: String?) = description?.trim()?.ifEmpty { null }

    private fun notFound() = NotFoundException("방송을 찾을 수 없습니다.")

    private fun Broadcast.toResult() = BroadcastResult(
        id = id,
        streamerId = streamerId,
        streamerNickname = streamerNickname,
        title = title,
        description = description,
        status = status,
        createdAt = createdAt,
    )

    companion object {
        const val PAGE_SIZE = 20
    }
}
