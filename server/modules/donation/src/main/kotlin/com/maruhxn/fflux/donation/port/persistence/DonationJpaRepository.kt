package com.maruhxn.fflux.donation.port.persistence

import com.maruhxn.fflux.donation.application.required.DonationRepository
import com.maruhxn.fflux.donation.domain.Donation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** 확정·취소는 결제사 호출과 트랜잭션을 나누므로, 조건부 UPDATE가 저마다 짧은 트랜잭션을 연다. */
interface DonationJpaRepository : JpaRepository<Donation, Long>, DonationRepository {

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE Donation d SET d.status = com.maruhxn.fflux.donation.domain.DonationStatus.DONE, d.paymentKey = :paymentKey, d.confirmedAt = :now " +
            "WHERE d.orderId = :orderId AND d.status = com.maruhxn.fflux.donation.domain.DonationStatus.PENDING",
    )
    override fun markDone(orderId: String, paymentKey: String, now: LocalDateTime): Int

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE Donation d SET d.status = com.maruhxn.fflux.donation.domain.DonationStatus.CANCELED " +
            "WHERE d.orderId = :orderId AND d.status = com.maruhxn.fflux.donation.domain.DonationStatus.PENDING",
    )
    override fun markCanceled(orderId: String): Int
}
