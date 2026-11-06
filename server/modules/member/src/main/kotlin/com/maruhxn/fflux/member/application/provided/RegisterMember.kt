package com.maruhxn.fflux.member.application.provided

interface RegisterMember {
    /** 회원을 만들고 회원 ID를 돌려준다. 이미 쓰이는 아이디면 ConflictException. */
    fun register(command: RegisterMemberCommand): Long
}

data class RegisterMemberCommand(
    val username: String,
    val password: String,
    val nickname: String,
) {
    /** 로그나 예외 메시지에 평문 비밀번호가 찍히지 않게 가린다. */
    override fun toString() = "RegisterMemberCommand(username=$username, password=****, nickname=$nickname)"
}
