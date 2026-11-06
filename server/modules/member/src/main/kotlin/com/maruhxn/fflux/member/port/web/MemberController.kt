package com.maruhxn.fflux.member.port.web

import com.maruhxn.fflux.member.application.provided.RegisterMember
import com.maruhxn.fflux.member.application.provided.RegisterMemberCommand
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/members")
class MemberController(private val registerMember: RegisterMember) {

    @PostMapping
    fun signUp(@Valid @RequestBody request: SignUpRequest): ResponseEntity<SignUpResponse> {
        val memberId = registerMember.register(
            RegisterMemberCommand(request.username, request.password, request.nickname),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(SignUpResponse(memberId))
    }
}

data class SignUpRequest(
    @Pattern(regexp = "^[a-z0-9_]{4,20}$", message = "아이디는 영문 소문자, 숫자, _ 4~20자입니다.")
    val username: String,
    @Size(min = 8, max = 64, message = "비밀번호는 8~64자입니다.")
    val password: String,
    @Pattern(regexp = "^\\S.{0,10}\\S$", message = "닉네임은 2~12자이고 앞뒤 공백이 없어야 합니다.")
    val nickname: String,
)

data class SignUpResponse(val memberId: Long)
