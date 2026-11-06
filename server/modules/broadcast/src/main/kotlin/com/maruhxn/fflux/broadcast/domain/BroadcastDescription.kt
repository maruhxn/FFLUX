package com.maruhxn.fflux.broadcast.domain

import com.maruhxn.fflux.common.error.BadRequestException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/** 방송 설명. 앞뒤 공백을 지운 뒤 1~500자다. 비었으면 설명이 없는 것이므로 [of]가 null을 돌려준다. */
@Embeddable
class BroadcastDescription private constructor(value: String) {

    @Column(name = "description", length = MAX_LENGTH)
    val value: String = value

    init {
        if (value.length > MAX_LENGTH) throw BadRequestException("설명은 ${MAX_LENGTH}자 이하입니다.", field = "description")
    }

    override fun equals(other: Any?) = other is BroadcastDescription && value == other.value
    override fun hashCode() = value.hashCode()
    override fun toString() = value

    companion object {
        const val MAX_LENGTH = 500

        fun of(raw: String?): BroadcastDescription? = raw?.trim()?.ifEmpty { null }?.let(::BroadcastDescription)
    }
}
