package com.maruhxn.fflux.member.domain

import com.maruhxn.fflux.common.error.BadRequestException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/** 로그인 아이디. 영문 소문자, 숫자, `_` 4~20자다. 공백을 지우지 않고 그대로 검사한다. */
@Embeddable
@ConsistentCopyVisibility
data class Username private constructor(
    @Column(name = "username", nullable = false, unique = true, length = MAX_LENGTH)
    val value: String,
) {
    init {
        if (!PATTERN.matches(value)) {
            throw BadRequestException("아이디는 영문 소문자, 숫자, _ ${MIN_LENGTH}~${MAX_LENGTH}자입니다.", field = "username")
        }
    }

    companion object {
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = 20
        private val PATTERN = Regex("^[a-z0-9_]{$MIN_LENGTH,$MAX_LENGTH}$")

        fun of(raw: String) = Username(raw)
    }
}
