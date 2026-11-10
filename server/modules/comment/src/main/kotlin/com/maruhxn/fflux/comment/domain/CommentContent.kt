package com.maruhxn.fflux.comment.domain

import com.maruhxn.fflux.common.error.BadRequestException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/** 댓글 본문. 앞뒤 공백을 지운 뒤 1~500자여야 한다. */
@Embeddable
@ConsistentCopyVisibility
data class CommentContent private constructor(
    @Column(name = "content", nullable = false, length = MAX_LENGTH)
    val value: String,
) {
    init {
        if (value.isEmpty()) throw BadRequestException("댓글 내용을 입력하세요.", field = "content")
        if (value.length > MAX_LENGTH) throw BadRequestException("댓글은 ${MAX_LENGTH}자 이하입니다.", field = "content")
    }

    companion object {
        const val MAX_LENGTH = 500

        fun of(raw: String) = CommentContent(raw.trim())
    }
}
