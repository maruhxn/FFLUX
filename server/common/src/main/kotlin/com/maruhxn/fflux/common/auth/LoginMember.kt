package com.maruhxn.fflux.common.auth

/**
 * web 포트의 컨트롤러 파라미터(`@LoginMember memberId: Long`)에 붙여 현재 로그인한 회원 ID를 받는다.
 * 세션에서 값을 꺼내는 리졸버는 `app`에 있어서, 모듈은 Spring Security 타입을 알 필요가 없다.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class LoginMember
