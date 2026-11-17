package com.maruhxn.fflux.broadcast.port.persistence

import com.maruhxn.fflux.broadcast.application.required.StreamerSummary
import com.maruhxn.fflux.broadcast.domain.Broadcast
import com.maruhxn.fflux.broadcast.domain.BroadcastStatus

interface BroadcastRepositoryCustom {
    fun findPage(status: BroadcastStatus?, streamerId: Long?, keyword: String?, cursor: Long?, limit: Int): List<Broadcast>

    fun findStreamers(keyword: String, statuses: Collection<BroadcastStatus>, limit: Int): List<StreamerSummary>
}
