package com.maruhxn.fflux.member.domain

import com.maruhxn.fflux.common.error.BadRequestException

/** 가입할 때 받는 평문 비밀번호. 8~64자다. 저장하지 않고 암호화한 값만 [Member]에 둔다. */
@ConsistentCopyVisibility
data class Password private constructor(val value: String) {

    init {
        if (value.length !in MIN_LENGTH..MAX_LENGTH) {
            throw BadRequestException("비밀번호는 ${MIN_LENGTH}~${MAX_LENGTH}자입니다.", field = "password")
        }
    }

    /** 로그나 예외 메시지에 비밀번호가 찍히지 않게 가린다. */
    override fun toString() = "Password(****)"

    companion object {
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 64

        fun of(raw: String) = Password(raw)
    }
}
