package com.maruhxn.fflux.member.application.provided

interface FindMember {
    fun findByUsername(username: String): MemberSummary?
}

data class MemberSummary(
    val memberId: Long,
    val nickname: String,
)
