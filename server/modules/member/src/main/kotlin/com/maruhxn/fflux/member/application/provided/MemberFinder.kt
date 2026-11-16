package com.maruhxn.fflux.member.application.provided

interface MemberFinder {
    fun findByUsername(username: String): MemberSummary?

    fun exists(memberId: Long): Boolean
}

data class MemberSummary(
    val memberId: Long,
    val nickname: String,
)
