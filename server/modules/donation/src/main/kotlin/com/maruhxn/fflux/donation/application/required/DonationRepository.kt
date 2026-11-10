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

    /** 방송의 DONE 후원을 ID 내림차순으로 [limit]개까지 읽는다. [cursor]가 있으면 그보다 작은 ID만 읽는다. */
    fun findDonePage(broadcastId: Long, cursor: Long?, limit: Int): List<Donation>

    /** 방송의 DONE 후원 금액 합계. 없으면 0. */
    fun sumDoneAmount(broadcastId: Long): Long
}
