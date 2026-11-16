package com.maruhxn.fflux.member.application.provided

interface MemberFinder {
    fun findByUsername(username: String): MemberSummary?

    fun exists(memberId: Long): Boolean

    /** 있는 회원만 담는다. */
    fun findNicknames(memberIds: Collection<Long>): Map<Long, String>
}

data class MemberSummary(
    val memberId: Long,
    val nickname: String,
)
