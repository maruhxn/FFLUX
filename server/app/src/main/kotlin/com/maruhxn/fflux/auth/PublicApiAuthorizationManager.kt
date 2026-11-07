package com.maruhxn.fflux.auth

import com.maruhxn.fflux.common.auth.LoginMember
import com.maruhxn.fflux.common.auth.PublicApi
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.authorization.AuthenticatedAuthorizationManager
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.authorization.AuthorizationResult
import org.springframework.security.core.Authentication
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.util.function.Supplier

/**
 * `/api` 요청의 인가. 요청을 처리할 핸들러 메서드에 [PublicApi]가 있으면 허용하고, 없으면 로그인을 요구한다.
 * 공개 여부를 컨트롤러 옆에서 선언하게 해서, 새 API가 생겨도 Security 설정을 고치지 않는다.
 */
@Component
class PublicApiAuthorizationManager(
    @Qualifier("requestMappingHandlerMapping") private val handlerMapping: RequestMappingHandlerMapping,
) : AuthorizationManager<RequestAuthorizationContext> {

    private val authenticated = AuthenticatedAuthorizationManager.authenticated<RequestAuthorizationContext>()

    init {
        verifyPublicApis(handlerMapping.handlerMethods.values)
    }

    override fun authorize(
        authentication: Supplier<out Authentication?>,
        context: RequestAuthorizationContext,
    ): AuthorizationResult? =
        if (isPublicApi(context)) AuthorizationDecision(true) else authenticated.authorize(authentication, context)

    private fun isPublicApi(context: RequestAuthorizationContext): Boolean {
        // 핸들러를 못 찾거나(404) 메서드·미디어 타입이 맞지 않아 예외가 나면 공개로 보지 않는다(기본 거절).
        val handler = runCatching { handlerMapping.getHandler(context.request)?.handler }.getOrNull()
        return (handler as? HandlerMethod)?.hasMethodAnnotation(PublicApi::class.java) == true
    }

    companion object {
        /** 공개 API가 로그인 회원을 non-null로 받으면 비로그인 호출에서 반드시 실패하므로, 앱 시작 단계에서 막는다. */
        fun verifyPublicApis(handlerMethods: Collection<HandlerMethod>) {
            val conflicts = handlerMethods.filter { method ->
                method.hasMethodAnnotation(PublicApi::class.java) &&
                    method.methodParameters.any { it.parameterType == LoginMember::class.java && !it.isOptional }
            }
            check(conflicts.isEmpty()) {
                "@PublicApi 메서드는 LoginMember를 nullable로만 받을 수 있습니다: ${conflicts.joinToString { it.shortLogMessage }}"
            }
        }
    }
}
