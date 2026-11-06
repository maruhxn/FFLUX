package com.maruhxn.fflux.broadcast.application.required

import com.maruhxn.fflux.broadcast.domain.Broadcast
import com.maruhxn.fflux.broadcast.domain.BroadcastStatus

interface BroadcastRepository {
    fun save(broadcast: Broadcast): Broadcast

    /** 삭제되지 않은 방송. */
    fun findByIdAndDeletedAtIsNull(id: Long): Broadcast?

    /** 삭제되지 않은 방송이 있는지. */
    fun existsByIdAndDeletedAtIsNull(id: Long): Boolean

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
     * [status], [streamerId]가 null이면 그 조건을 걸지 않고, [cursor]가 있으면 그보다 작은 ID만 읽는다.
     */
    fun findPage(status: BroadcastStatus?, streamerId: Long?, cursor: Long?, limit: Int): List<Broadcast>
}
