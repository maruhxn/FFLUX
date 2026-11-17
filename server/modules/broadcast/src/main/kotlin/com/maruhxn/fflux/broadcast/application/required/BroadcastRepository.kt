package com.maruhxn.fflux.broadcast.application.required

import com.maruhxn.fflux.broadcast.domain.Broadcast
import com.maruhxn.fflux.broadcast.domain.BroadcastStatus

interface BroadcastRepository {
    fun save(broadcast: Broadcast): Broadcast

    /** 삭제되지 않은 방송. */
    fun findByIdAndDeletedAtIsNull(id: Long): Broadcast?

    /** 삭제되지 않은 [status] 방송들. */
    fun findAllByIdInAndStatusAndDeletedAtIsNull(ids: Collection<Long>, status: BroadcastStatus): List<Broadcast>

    /** 스트리머들의 삭제되지 않은 [status] 방송들. */
    fun findAllByStreamerIdInAndStatusAndDeletedAtIsNull(streamerIds: Collection<Long>, status: BroadcastStatus): List<Broadcast>

    /** 스트리머의 삭제되지 않은 [statuses] 방송 중 가장 최근 것. */
    fun findFirstByStreamerIdAndStatusInAndDeletedAtIsNullOrderByIdDesc(streamerId: Long, statuses: Collection<BroadcastStatus>): Broadcast?

    /** 스트리머의 삭제되지 않은 [status] 방송 중 하나. */
    fun findFirstByStreamerIdAndStatusAndDeletedAtIsNull(streamerId: Long, status: BroadcastStatus): Broadcast?

    /** 삭제되지 않은 [status] 방송 전부. */
    fun findAllByStatusAndDeletedAtIsNull(status: BroadcastStatus): List<Broadcast>

    /** 삭제되지 않은 방송을 행 잠금(`SELECT … FOR UPDATE`)으로 읽는다. */
    fun findForUpdate(id: Long): Broadcast?

    /** 삭제되지 않은 방송을 송출 경로로 찾는다. */
    fun findByMediaPathAndDeletedAtIsNull(mediaPath: String): Broadcast?

    /** 삭제되지 않은 방송을 송출 경로로 찾아 행 잠금으로 읽는다. */
    fun findForUpdateByMediaPath(mediaPath: String): Broadcast?

    /** 스트리머에게 삭제되지 않은 [status] 방송이 있는지. */
    fun existsByStreamerIdAndStatusAndDeletedAtIsNull(streamerId: Long, status: BroadcastStatus): Boolean

    /**
     * 삭제되지 않은 방송을 ID 내림차순(최신순)으로 [limit]개까지 읽는다.
     * [status], [streamerId], [keyword]가 null이면 그 조건을 걸지 않고, [cursor]가 있으면 그보다 작은 ID만 읽는다.
     * [keyword]는 제목이나 스트리머 닉네임에 들어 있으면 맞는다(대소문자는 DB 정렬 규칙을 따라 가리지 않는다).
     */
    fun findPage(status: BroadcastStatus?, streamerId: Long?, keyword: String?, cursor: Long?, limit: Int): List<Broadcast>

    /**
     * 삭제되지 않은 [statuses] 방송이 있고 닉네임에 [keyword]가 든 스트리머를 [limit]명까지 읽는다.
     * LIVE 방송이 있는 스트리머가 먼저이고, 그 안에서는 최근에 방송한(방송 ID가 큰) 순서다. 자르기 전에 정렬해야 방송 중인 스트리머가 빠지지 않는다.
     * 대소문자는 DB 정렬 규칙을 따라 가리지 않는다.
     */
    fun findStreamers(keyword: String, statuses: Collection<BroadcastStatus>, limit: Int): List<StreamerSummary>
}

data class StreamerSummary(val streamerId: Long, val nickname: String)
