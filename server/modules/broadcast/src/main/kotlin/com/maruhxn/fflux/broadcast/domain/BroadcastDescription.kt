package com.maruhxn.fflux.broadcast.domain

import com.maruhxn.fflux.common.error.BadRequestException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/** 방송 설명. 앞뒤 공백을 지운 뒤 1~500자다. 비었으면 설명이 없는 것이므로 [of]가 null을 돌려준다. */
@Embeddable
@ConsistentCopyVisibility
data class BroadcastDescription private constructor(
    @Column(name = "description", length = MAX_LENGTH)
    val value: String,
) {
    init {
        if (value.length > MAX_LENGTH) throw BadRequestException("설명은 ${MAX_LENGTH}자 이하입니다.", field = "description")
    }

    companion object {
        const val MAX_LENGTH = 500

        fun of(raw: String?): BroadcastDescription? = raw?.trim()?.ifEmpty { null }?.let(::BroadcastDescription)
    }
}
