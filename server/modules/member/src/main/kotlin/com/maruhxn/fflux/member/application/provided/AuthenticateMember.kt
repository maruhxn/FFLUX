package com.maruhxn.fflux.member.application.provided

interface AuthenticateMember {
    /** 아이디와 비밀번호가 맞으면 회원 요약을, 틀리면 UnauthorizedException을 던진다. */
    fun authenticate(username: String, password: String): MemberSummary
}
