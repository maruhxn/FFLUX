package com.maruhxn.fflux.member.application.required

import com.maruhxn.fflux.member.domain.Member
import com.maruhxn.fflux.member.domain.Username

interface MemberRepository {
    fun save(member: Member): Member
    fun findByUsername(username: Username): Member?
    fun existsByUsername(username: Username): Boolean
}
