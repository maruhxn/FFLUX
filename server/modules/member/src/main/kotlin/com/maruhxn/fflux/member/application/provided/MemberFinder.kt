package com.maruhxn.fflux.member.application.provided

interface MemberFinder {
    fun findByUsername(username: String): MemberSummary?
}

data class MemberSummary(
    val memberId: Long,
    val nickname: String,
)
