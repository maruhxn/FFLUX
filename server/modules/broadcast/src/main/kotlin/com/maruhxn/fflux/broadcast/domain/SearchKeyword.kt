package com.maruhxn.fflux.broadcast.domain

import com.maruhxn.fflux.common.error.BadRequestException

/** 방송·스트리머 검색어. 앞뒤 공백을 지운 뒤 50자(제목 최대 길이) 이하여야 한다. 영속화하지 않는다. */
@ConsistentCopyVisibility
data class SearchKeyword private constructor(val value: String) {
    init {
        if (value.length > MAX_LENGTH) throw BadRequestException("검색어는 ${MAX_LENGTH}자 이하입니다.", field = "q")
    }

    companion object {
        const val MAX_LENGTH = BroadcastTitle.MAX_LENGTH

        /** 비었거나 공백뿐이면 검색어가 없는 것이다. */
        fun of(raw: String?): SearchKeyword? = raw?.trim()?.takeIf { it.isNotEmpty() }?.let(::SearchKeyword)
    }
}
