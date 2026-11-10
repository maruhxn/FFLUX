package com.maruhxn.fflux.chat.domain

import com.maruhxn.fflux.common.error.BadRequestException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/** 채팅 본문. 앞뒤 공백을 지운 뒤 1~200자여야 한다. */
@Embeddable
@ConsistentCopyVisibility
data class ChatContent private constructor(
    @Column(name = "content", nullable = false, length = MAX_LENGTH)
    val value: String,
) {
    init {
        if (value.isEmpty()) throw BadRequestException("채팅 내용을 입력하세요.", field = "content")
        if (value.length > MAX_LENGTH) throw BadRequestException("채팅은 ${MAX_LENGTH}자 이하입니다.", field = "content")
    }

    companion object {
        const val MAX_LENGTH = 200

        fun of(raw: String) = ChatContent(raw.trim())
    }
}
