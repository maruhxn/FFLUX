package com.maruhxn.fflux.auth

import com.maruhxn.fflux.support.IntegrationTest
import com.maruhxn.fflux.support.TEST_FRONT_ORIGIN
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

class CorsTest : IntegrationTest() {

    @Test
    fun `허용한 프론트 출처의 사전 요청은 쿠키를 실은 요청을 허락한다`() {
        val response = http.options().uri("/api/auth/login")
            .header(HttpHeaders.ORIGIN, TEST_FRONT_ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type")
            .retrieve().toBodilessEntity()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(TEST_FRONT_ORIGIN, response.headers.accessControlAllowOrigin)
        assertEquals(true, response.headers.accessControlAllowCredentials)
    }

    @Test
    fun `허용한 프론트 출처의 요청에는 응답을 읽을 수 있게 출처를 돌려준다`() {
        val response = http.get().uri("/api/broadcasts?status=LIVE")
            .header(HttpHeaders.ORIGIN, TEST_FRONT_ORIGIN)
            .retrieve().toBodilessEntity()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(TEST_FRONT_ORIGIN, response.headers.accessControlAllowOrigin)
    }

    @Test
    fun `허용하지 않은 출처의 요청은 처리하지 않고 403`() {
        val response = http.method(HttpMethod.POST).uri("/api/auth/logout")
            .header(HttpHeaders.ORIGIN, "https://evil.kro.kr")
            .retrieve().toBodilessEntity()

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }
}
