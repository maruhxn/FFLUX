package com.maruhxn.fflux.broadcast

import com.maruhxn.fflux.auth.TestAccountSeeder
import com.maruhxn.fflux.support.IntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals

/**
 * 송출·종료 흐름 통합 테스트의 기반. 스트리머·타인으로 로그인해 두고, `/api`와 MediaMTX가 부르는 `/internal`을 호출하는 헬퍼를 둔다.
 */
abstract class BroadcastFlowTest : IntegrationTest() {

    @Autowired
    private lateinit var testAccountSeeder: TestAccountSeeder

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    protected lateinit var streamer: String
    protected lateinit var other: String

    @BeforeEach
    fun logIn() {
        testAccountSeeder.run(DefaultApplicationArguments())
        streamer = testLogin("streamer")
        other = testLogin("viewer")
    }

    /** 실제 흐름(토큰 발급 → publish 인증 → available 훅)으로 방송을 LIVE로 만든다. */
    protected fun goLive(id: Long, cookie: String = streamer): Long {
        val token = tokenOf(issue(id, cookie, record = false))
        assertEquals(HttpStatus.OK, auth("publish", "live/$id", token).statusCode)
        available("live/$id")
        assertEquals("LIVE", statusOf(id))
        return id
    }

    @Suppress("UNCHECKED_CAST")
    protected fun issue(id: Long, cookie: String?, record: Boolean = false): ResponseEntity<Map<String, Any?>> =
        http.post().uri("/api/broadcasts/{id}/publish-token", id).withCookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("record" to record))
            .retrieve().toEntity(Map::class.java) as ResponseEntity<Map<String, Any?>>

    protected fun tokenOf(response: ResponseEntity<Map<String, Any?>>): String {
        assertEquals(HttpStatus.OK, response.statusCode)
        return response.body!!["token"] as String
    }

    /** MediaMTX가 보내는 인증 요청 본문 형태 그대로 보낸다. */
    protected fun auth(action: String, path: String, token: String?): ResponseEntity<Void> =
        http.post().uri("/internal/mediamtx/auth")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf(
                    "user" to "", "password" to "", "token" to (token ?: ""), "ip" to "172.18.0.1",
                    "action" to action, "path" to path, "protocol" to "webrtc", "id" to null, "query" to "",
                ),
            )
            .retrieve().toBodilessEntity()

    protected fun available(path: String): ResponseEntity<Void> =
        http.post().uri("/internal/mediamtx/available")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("path" to path))
            .retrieve().toBodilessEntity()

    protected fun memberIdOf(username: String): Long =
        jdbcTemplate.queryForObject("SELECT id FROM member WHERE username = ?", Long::class.java, username)!!

    protected fun statusOf(id: Long): String =
        jdbcTemplate.queryForObject("SELECT status FROM broadcast WHERE id = ?", String::class.java, id)!!

    protected fun setStatus(id: Long, status: String) {
        jdbcTemplate.update("UPDATE broadcast SET status = ? WHERE id = ?", status, id)
    }

    protected fun delete(id: Long, cookie: String?): ResponseEntity<Void> =
        http.delete().uri("/api/broadcasts/{id}", id).withCookie(cookie).retrieve().toBodilessEntity()

    protected fun register(cookie: String): Long {
        val response = http.post().uri("/api/broadcasts").withCookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("title" to "방송"))
            .retrieve().toEntity(Map::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        return (response.body!!["id"] as Number).toLong()
    }

    protected fun testLogin(account: String): String =
        sessionCookieOf(
            http.post().uri("/api/auth/test-login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("account" to account))
                .retrieve().toBodilessEntity().headers,
        )

    protected fun <S : RestClient.RequestHeadersSpec<S>> S.withCookie(cookie: String?): S =
        apply { if (cookie != null) header(HttpHeaders.COOKIE, cookie) }

    protected fun unavailable(path: String): ResponseEntity<Void> =
        http.post().uri("/internal/mediamtx/unavailable")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("path" to path))
            .retrieve().toBodilessEntity()

    protected fun end(id: Long, cookie: String?): ResponseEntity<Void> =
        http.post().uri("/api/broadcasts/{id}/end", id).withCookie(cookie).retrieve().toBodilessEntity()
}
