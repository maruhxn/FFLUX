package com.maruhxn.fflux.auth

import com.maruhxn.fflux.common.auth.LoginMember
import com.maruhxn.fflux.common.error.UnauthorizedException
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/** `@LoginMember memberId: Long` 파라미터에 세션의 회원 ID를 넣는다. 로그인하지 않았으면 401. */
@Component
class LoginMemberArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(LoginMember::class.java) &&
            (parameter.parameterType == Long::class.java || parameter.parameterType == java.lang.Long::class.java)

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? LoginPrincipal
            ?: throw UnauthorizedException("로그인이 필요합니다.")
        return principal.memberId
    }
}
