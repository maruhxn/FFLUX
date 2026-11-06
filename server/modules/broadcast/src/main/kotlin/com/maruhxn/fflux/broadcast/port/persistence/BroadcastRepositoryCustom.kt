package com.maruhxn.fflux.broadcast.port.persistence

import com.maruhxn.fflux.broadcast.domain.Broadcast
import com.maruhxn.fflux.broadcast.domain.BroadcastStatus

interface BroadcastRepositoryCustom {
    fun findPage(status: BroadcastStatus?, streamerId: Long?, cursor: Long?, limit: Int): List<Broadcast>
}
