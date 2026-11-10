package com.maruhxn.fflux.donation.domain

import com.maruhxn.fflux.common.error.BadRequestException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/** 후원 메시지. 앞뒤 공백을 지운 뒤 100자 이하여야 한다. */
@Embeddable
@ConsistentCopyVisibility
data class DonationMessage private constructor(
    @Column(name = "message", length = MAX_LENGTH)
    val value: String,
) {
    init {
        if (value.length > MAX_LENGTH) throw BadRequestException("후원 메시지는 ${MAX_LENGTH}자 이하입니다.", field = "message")
    }

    companion object {
        const val MAX_LENGTH = 100

        /** 없거나 공백만이면 메시지 없음(null)이다. */
        fun of(raw: String?): DonationMessage? = raw?.trim()?.takeIf { it.isNotEmpty() }?.let(::DonationMessage)
    }
}
