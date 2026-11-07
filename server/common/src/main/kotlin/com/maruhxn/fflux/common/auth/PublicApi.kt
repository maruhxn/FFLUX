package com.maruhxn.fflux.common.auth

/**
 * 로그인 없이 부를 수 있는 `/api` 핸들러 메서드에 붙인다. 붙이지 않은 `/api` 핸들러는 비로그인이면 401이다(기본 거절).
 * 공개 API는 [LoginMember]를 `LoginMember?`로만 받는다. 비로그인이면 null이다.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PublicApi
