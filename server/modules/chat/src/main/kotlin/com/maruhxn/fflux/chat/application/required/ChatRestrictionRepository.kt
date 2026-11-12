package com.maruhxn.fflux.chat.application.required

import com.maruhxn.fflux.chat.domain.ChatRestriction

/** 기간이 지난 금지도 행으로 남아 있을 수 있다. 금지 중인지는 [ChatRestriction.isActive]로 본다. */
interface ChatRestrictionRepository {
    fun save(restriction: ChatRestriction): ChatRestriction

    fun findByStreamerIdAndMemberId(streamerId: Long, memberId: Long): ChatRestriction?

    /** 스트리머의 금지 전부(기간이 지난 것 포함), 최근에 건 것부터. */
    fun findAllByStreamerIdOrderByRestrictedAtDesc(streamerId: Long): List<ChatRestriction>

    /** 지운 행 수. */
    fun deleteByStreamerIdAndMemberId(streamerId: Long, memberId: Long): Long
}
