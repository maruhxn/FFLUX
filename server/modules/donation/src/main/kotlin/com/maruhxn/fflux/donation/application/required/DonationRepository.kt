package com.maruhxn.fflux.donation.application.required

import com.maruhxn.fflux.donation.domain.Donation
import java.time.LocalDateTime

interface DonationRepository {
    fun save(donation: Donation): Donation

    fun findByOrderId(orderId: String): Donation?

    /** PENDING이면 DONE으로 바꾸고 바뀐 행 수(0 또는 1)를 돌려준다. */
    fun markDone(orderId: String, paymentKey: String, now: LocalDateTime): Int

    /** PENDING이면 CANCELED로 바꾸고 바뀐 행 수(0 또는 1)를 돌려준다. */
    fun markCanceled(orderId: String): Int
}
