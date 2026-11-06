package com.maruhxn.fflux.member.application.provided

interface RegisterMember {
    /** 회원을 만들고 회원 ID를 돌려준다. 이미 쓰이는 아이디면 ConflictException. */
    fun register(command: RegisterMemberCommand): Long
}

data class RegisterMemberCommand(
    val username: String,
    val password: String,
    val nickname: String,
)
