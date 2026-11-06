package com.maruhxn.fflux.member.port.persistence

import com.maruhxn.fflux.member.application.required.MemberRepository
import com.maruhxn.fflux.member.domain.Member
import org.springframework.data.jpa.repository.JpaRepository

interface MemberJpaRepository : JpaRepository<Member, Long>, MemberRepository
