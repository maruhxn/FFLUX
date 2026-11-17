package com.maruhxn.fflux.broadcast.port.persistence

import com.maruhxn.fflux.broadcast.application.required.StreamerSummary
import com.maruhxn.fflux.broadcast.domain.Broadcast
import com.maruhxn.fflux.broadcast.domain.BroadcastStatus
import com.maruhxn.fflux.broadcast.domain.QBroadcast.Companion.broadcast
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.CaseBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager

class BroadcastRepositoryCustomImpl(entityManager: EntityManager) : BroadcastRepositoryCustom {

    private val queryFactory = JPAQueryFactory(entityManager)

    override fun findPage(status: BroadcastStatus?, streamerId: Long?, keyword: String?, cursor: Long?, limit: Int): List<Broadcast> =
        queryFactory.selectFrom(broadcast)
            .where(
                broadcast.deletedAt.isNull,
                status?.let { broadcast.status.eq(it) },
                streamerId?.let { broadcast.streamerId.eq(it) },
                keyword?.let { broadcast.title.value.contains(it).or(broadcast.streamerNickname.contains(it)) },
                cursor?.let { broadcast.id.lt(it) },
            )
            .orderBy(broadcast.id.desc())
            .limit(limit.toLong())
            .fetch()

    /** 닉네임은 방송마다 같은 스냅샷이라 스트리머와 함께 묶는다. */
    override fun findStreamers(keyword: String, statuses: Collection<BroadcastStatus>, limit: Int): List<StreamerSummary> =
        queryFactory.select(Projections.constructor(StreamerSummary::class.java, broadcast.streamerId, broadcast.streamerNickname))
            .from(broadcast)
            .where(
                broadcast.deletedAt.isNull,
                broadcast.status.`in`(statuses),
                broadcast.streamerNickname.contains(keyword),
            )
            .groupBy(broadcast.streamerId, broadcast.streamerNickname)
            .orderBy(
                CaseBuilder().`when`(broadcast.status.eq(BroadcastStatus.LIVE)).then(1).otherwise(0).max().desc(),
                broadcast.id.max().desc(),
            )
            .limit(limit.toLong())
            .fetch()
}
