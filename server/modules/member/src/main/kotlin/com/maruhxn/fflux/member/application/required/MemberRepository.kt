package com.maruhxn.fflux.member.application.required

import com.maruhxn.fflux.member.domain.Member

interface MemberRepository {
    fun save(member: Member): Member
    fun findByUsername(username: String): Member?
    fun existsByUsername(username: String): Boolean
}
