package com.maruhxn.fflux.broadcast.domain

import com.maruhxn.fflux.common.error.BadRequestException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/** 방송 제목. 앞뒤 공백을 지운 뒤 1~50자여야 한다. */
@Embeddable
@ConsistentCopyVisibility
data class BroadcastTitle private constructor(
    @Column(name = "title", nullable = false, length = MAX_LENGTH)
    val value: String,
) {
    init {
        if (value.isEmpty()) throw BadRequestException("제목을 입력하세요.", field = "title")
        if (value.length > MAX_LENGTH) throw BadRequestException("제목은 ${MAX_LENGTH}자 이하입니다.", field = "title")
    }

    companion object {
        const val MAX_LENGTH = 50

        fun of(raw: String) = BroadcastTitle(raw.trim())
    }
}
