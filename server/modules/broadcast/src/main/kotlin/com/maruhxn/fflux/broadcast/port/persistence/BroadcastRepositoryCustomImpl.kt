package com.maruhxn.fflux.broadcast.port.persistence

import com.maruhxn.fflux.broadcast.domain.Broadcast
import com.maruhxn.fflux.broadcast.domain.BroadcastStatus
import com.maruhxn.fflux.broadcast.domain.QBroadcast.Companion.broadcast
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager

class BroadcastRepositoryCustomImpl(entityManager: EntityManager) : BroadcastRepositoryCustom {

    private val queryFactory = JPAQueryFactory(entityManager)

    override fun findPage(status: BroadcastStatus?, streamerId: Long?, cursor: Long?, limit: Int): List<Broadcast> =
        queryFactory.selectFrom(broadcast)
            .where(
                broadcast.deletedAt.isNull,
                status?.let { broadcast.status.eq(it) },
                streamerId?.let { broadcast.streamerId.eq(it) },
                cursor?.let { broadcast.id.lt(it) },
            )
            .orderBy(broadcast.id.desc())
            .limit(limit.toLong())
            .fetch()
}
