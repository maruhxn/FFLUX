package com.maruhxn.fflux.member.port.web

import com.maruhxn.fflux.common.auth.PublicApi
import com.maruhxn.fflux.member.application.provided.MemberRegister
import com.maruhxn.fflux.member.application.provided.RegisterMemberCommand
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/members")
class MemberController(private val memberRegister: MemberRegister) {

    @PublicApi
    @PostMapping
    fun signUp(@RequestBody request: SignUpRequest): ResponseEntity<SignUpResponse> {
        val memberId = memberRegister.register(
            RegisterMemberCommand(request.username, request.password, request.nickname),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(SignUpResponse(memberId))
    }
}

/** 아이디·비밀번호·닉네임 규칙은 도메인 값 객체가 검사한다. */
data class SignUpRequest(
    val username: String,
    val password: String,
    val nickname: String,
)

data class SignUpResponse(val memberId: Long)
