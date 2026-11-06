package com.maruhxn.fflux.auth

import com.maruhxn.fflux.common.error.NotFoundException
import com.maruhxn.fflux.member.application.provided.AuthenticateMember
import com.maruhxn.fflux.member.application.provided.FindMember
import com.maruhxn.fflux.member.application.provided.MemberSummary
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authenticateMember: AuthenticateMember,
    private val findMember: FindMember,
    private val securityContextRepository: SecurityContextRepository,
    private val testAccountProperties: TestAccountProperties,
) {

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): MeResponse {
        val member = authenticateMember.authenticate(request.username, request.password)
        return signIn(member, httpRequest, httpResponse)
    }

    /** 평가용: 로그인 화면의 "테스트 계정으로 로그인" 버튼. 비밀번호 없이 시드 계정으로 로그인한다. */
    @PostMapping("/test-login")
    fun testLogin(
        @Valid @RequestBody request: TestLoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): MeResponse {
        val member = TestAccount.ofUsername(request.account)
            ?.takeIf { testAccountProperties.enabled }
            ?.let { findMember.findByUsername(it.username) }
            ?: throw NotFoundException("테스트 계정을 찾을 수 없습니다.")
        return signIn(member, httpRequest, httpResponse)
    }

    @PostMapping("/logout")
    fun logout(httpRequest: HttpServletRequest): ResponseEntity<Unit> {
        httpRequest.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: LoginPrincipal): MeResponse =
        MeResponse(principal.memberId, principal.nickname)

    private fun signIn(
        member: MemberSummary,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): MeResponse {
        // 세션 고정 공격을 막기 위해 로그인 시 세션 ID를 바꾼다.
        httpRequest.getSession(false)?.let { httpRequest.changeSessionId() }
        val principal = LoginPrincipal(member.memberId, member.nickname)
        val context = SecurityContextHolder.createEmptyContext().apply {
            authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, emptyList())
        }
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, httpRequest, httpResponse)
        return MeResponse(member.memberId, member.nickname)
    }
}

data class LoginRequest(
    @NotBlank val username: String,
    @NotBlank val password: String,
)

data class TestLoginRequest(@NotBlank val account: String)

data class MeResponse(val memberId: Long, val nickname: String)
