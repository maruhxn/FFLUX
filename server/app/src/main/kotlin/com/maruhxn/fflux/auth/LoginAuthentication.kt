package com.maruhxn.fflux.auth

import com.maruhxn.fflux.common.auth.LoginMember
import org.springframework.security.authentication.AbstractAuthenticationToken

/**
 * 세션의 SecurityContext에 저장하는 인증. principal은 [LoginMember]다.
 * [getName]은 세션 테이블의 PRINCIPAL_NAME이 되므로 회원 ID를 쓴다.
 */
class LoginAuthentication(private val member: LoginMember) : AbstractAuthenticationToken(emptyList()) {

    init {
        isAuthenticated = true
    }

    override fun getPrincipal(): LoginMember = member

    override fun getCredentials(): Any? = null

    override fun getName(): String = member.memberId.toString()

    companion object {
        private const val serialVersionUID = 1L
    }
}
