package com.maruhxn.fflux.member.domain

import com.maruhxn.fflux.common.error.BadRequestException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/** 닉네임. 앞뒤 공백을 지운 뒤 2~12자여야 한다. */
@Embeddable
@ConsistentCopyVisibility
data class Nickname private constructor(
    @Column(name = "nickname", nullable = false, length = MAX_LENGTH)
    val value: String,
) {
    init {
        if (value.length !in MIN_LENGTH..MAX_LENGTH) {
            throw BadRequestException("닉네임은 ${MIN_LENGTH}~${MAX_LENGTH}자입니다.", field = "nickname")
        }
    }

    companion object {
        const val MIN_LENGTH = 2
        const val MAX_LENGTH = 12

        fun of(raw: String) = Nickname(raw.trim())
    }
}
