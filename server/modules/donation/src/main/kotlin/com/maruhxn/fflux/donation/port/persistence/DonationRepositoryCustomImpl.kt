package com.maruhxn.fflux.donation.port.persistence

import com.maruhxn.fflux.donation.domain.Donation
import com.maruhxn.fflux.donation.domain.DonationStatus
import com.maruhxn.fflux.donation.domain.QDonation.Companion.donation
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager

class DonationRepositoryCustomImpl(entityManager: EntityManager) : DonationRepositoryCustom {

    private val queryFactory = JPAQueryFactory(entityManager)

    override fun findDonePage(broadcastId: Long, cursor: Long?, limit: Int): List<Donation> =
        queryFactory.selectFrom(donation)
            .where(
                donation.broadcastId.eq(broadcastId),
                donation.status.eq(DonationStatus.DONE),
                cursor?.let { donation.id.lt(it) },
            )
            .orderBy(donation.id.desc())
            .limit(limit.toLong())
            .fetch()
}
