package com.maruhxn.fflux.donation.domain

import com.maruhxn.fflux.common.error.BadRequestException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/** 후원 금액(원). 1,000원 이상 1,000,000원 이하여야 한다. */
@Embeddable
@ConsistentCopyVisibility
data class DonationAmount private constructor(
    @Column(name = "amount", nullable = false)
    val value: Long,
) {
    init {
        if (value !in MIN..MAX) throw BadRequestException("후원 금액은 1,000원 이상 1,000,000원 이하입니다.", field = "amount")
    }

    companion object {
        const val MIN = 1_000L
        const val MAX = 1_000_000L

        fun of(value: Long) = DonationAmount(value)
    }
}
