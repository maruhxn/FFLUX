package com.maruhxn.fflux.common.auth

import java.io.Serializable

/**
 * 현재 로그인한 회원. web 포트의 컨트롤러가 `member: LoginMember` 파라미터로 받으면 그 API는 로그인이 필요하다.
 * 세션에 저장하는 값도 이 타입이라(Spring Session JDBC가 직렬화해 MySQL에 둔다) 모듈은 Spring Security 타입을 몰라도 된다.
 * `java.security.Principal`은 구현하지 않는다. 구현하면 Spring MVC 기본 리졸버가 이 파라미터를 가로챈다.
 */
data class LoginMember(
    val memberId: Long,
    val nickname: String,
) : Serializable {
    companion object {
        /** 세션에 저장된 직렬화 데이터와의 호환 버전. 필드를 바꾸면 올린다(기존 세션은 무효가 된다). */
        private const val serialVersionUID = 1L
    }
}
