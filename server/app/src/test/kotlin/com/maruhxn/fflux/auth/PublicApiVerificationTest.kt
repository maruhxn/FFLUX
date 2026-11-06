package com.maruhxn.fflux.auth

import com.maruhxn.fflux.common.auth.LoginMember
import com.maruhxn.fflux.common.auth.PublicApi
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.web.method.HandlerMethod

class PublicApiVerificationTest {

    @Test
    fun `PublicApi 메서드가 LoginMember를 받으면 앱 시작을 막는다`() {
        assertThrows<IllegalStateException> {
            PublicApiAuthorizationManager.verifyPublicApis(listOf(handlerOf("publicWithLoginMember")))
        }
    }

    @Test
    fun `PublicApi 메서드와 LoginMember 메서드가 따로 있으면 통과한다`() {
        assertDoesNotThrow {
            PublicApiAuthorizationManager.verifyPublicApis(
                listOf(handlerOf("publicOnly"), handlerOf("loginOnly")),
            )
        }
    }

    private fun handlerOf(name: String) =
        HandlerMethod(Handlers(), Handlers::class.java.declaredMethods.first { it.name == name })

    @Suppress("unused", "UNUSED_PARAMETER")
    private class Handlers {
        @PublicApi
        fun publicWithLoginMember(member: LoginMember) = Unit

        @PublicApi
        fun publicOnly() = Unit

        fun loginOnly(member: LoginMember) = Unit
    }
}
