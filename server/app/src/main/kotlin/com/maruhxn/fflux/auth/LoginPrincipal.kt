package com.maruhxn.fflux.auth

import java.io.Serializable
import java.security.Principal

/**
 * 세션에 저장하는 로그인 정보. Spring Session JDBC가 직렬화해 MySQL에 둔다.
 * [getName]은 세션 테이블의 PRINCIPAL_NAME이 되므로 회원 ID를 쓴다.
 */
data class LoginPrincipal(
    val memberId: Long,
    val nickname: String,
) : Principal, Serializable {
    override fun getName(): String = memberId.toString()

    companion object {
        /** 세션에 저장된 직렬화 데이터와의 호환 버전. 필드를 바꾸면 올린다(기존 세션은 무효가 된다). */
        private const val serialVersionUID = 1L
    }
}
